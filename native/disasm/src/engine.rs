// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Cross references and the call graph from the Rust Ghidra engine. They work for every architecture the engine
//! reads, so they are used where the ARM64 analysis of [`crate::analysis`] cannot help.

use std::collections::{BTreeMap, BTreeSet};
use std::fmt::Write;

use ghidra_decompiler::FunctionFingerprint;

use crate::decompile::{graph_of, intelligence_of, with_cached, with_open, Graph};

/// The most lines of a report.
const MAX_LINES: usize = 300_000;
/// The most names in one line of a function header.
const MAX_LISTED: usize = 12;

fn name_of(graph: &Graph, address: u64) -> String {
    match graph.names.get(&address) {
        Some(name) => crate::library::demangle(name),
        None => format!("sub_{:x}", address),
    }
}

fn limited(names: &BTreeSet<String>) -> String {
    let shown: Vec<&str> = names.iter().take(MAX_LISTED).map(String::as_str).collect();
    let mut text = shown.join(", ");
    if names.len() > MAX_LISTED {
        let _ = write!(text, ", and {} more", names.len() - MAX_LISTED);
    }
    text
}

/// The `callgraph` or `xrefs` report of the library at `path`.
pub fn report(path: &str, kind: &str) -> Result<String, String> {
    with_cached(path, |cached| {
        let graph = graph_of(cached)?;
        let mut out = String::new();
        match kind {
            "callgraph" => {
                let mut callees: BTreeMap<u64, BTreeSet<String>> = BTreeMap::new();
                for edge in &graph.edges {
                    callees.entry(edge.caller).or_default().insert(name_of(graph, edge.callee));
                }
                for (caller, names) in callees.iter().take(MAX_LINES) {
                    let _ = writeln!(out, "{} {:x}", name_of(graph, *caller), caller);
                    for name in names {
                        let _ = writeln!(out, "    -> {}", name);
                    }
                }
            }
            "xrefs" => {
                let mut callers: BTreeMap<u64, BTreeSet<String>> = BTreeMap::new();
                for edge in &graph.edges {
                    callers.entry(edge.callee).or_default().insert(format!(
                        "{} @ {:x}",
                        name_of(graph, edge.caller),
                        edge.call_site
                    ));
                }
                for (callee, names) in callers.iter().take(MAX_LINES) {
                    let _ = writeln!(out, "{} {:x}", name_of(graph, *callee), callee);
                    for name in names {
                        let _ = writeln!(out, "    <- {}", name);
                    }
                }
            }
            other => return Err(format!("Unknown report {}", other)),
        }
        if out.is_empty() {
            out.push_str("The engine found no calls in this library\n");
        }
        Ok(out)
    })
}

/// Comment lines for the top of the function at `address`: who calls it and what it calls.
/// Every line starts with `comment`.
pub fn function_header(path: &str, address: u64, comment: &str) -> Result<String, String> {
    with_cached(path, |cached| {
        let graph = graph_of(cached)?;
        let callers: BTreeSet<String> =
            graph.edges.iter().filter(|e| e.callee == address).map(|e| name_of(graph, e.caller)).collect();
        let callees: BTreeSet<String> =
            graph.edges.iter().filter(|e| e.caller == address).map(|e| name_of(graph, e.callee)).collect();
        let mut out = String::new();
        if !callers.is_empty() {
            let _ = writeln!(out, "{}xrefs to this function ({}): {}", comment, callers.len(), limited(&callers));
        }
        if !callees.is_empty() {
            let _ = writeln!(out, "{}calls ({}): {}", comment, callees.len(), limited(&callees));
        }
        Ok(out)
    })
}

fn confidence(value: impl std::fmt::Debug) -> String {
    format!("{:?}", value).to_lowercase()
}

fn bytes_text(sizes: &[u32]) -> String {
    if sizes.is_empty() {
        return "nothing seen".to_string();
    }
    let mut sizes = sizes.to_vec();
    sizes.sort_unstable();
    sizes.dedup();
    let list: Vec<String> = sizes.iter().map(|s| s.to_string()).collect();
    format!("{} bytes", list.join(" or "))
}

/// How alike two functions are, from 0 to 1: the same bytes, or the same mix of operations.
fn similarity(a: &FunctionFingerprint, b: &FunctionFingerprint) -> f64 {
    if a.byte_hash == b.byte_hash && a.instruction_count == b.instruction_count {
        return 1.0;
    }
    let (mut dot, mut norm_a, mut norm_b) = (0.0f64, 0.0f64, 0.0f64);
    for (op, count) in &a.opcode_histogram {
        let count = *count as f64;
        norm_a += count * count;
        if let Some(other) = b.opcode_histogram.get(op) {
            dot += count * (*other as f64);
        }
    }
    for count in b.opcode_histogram.values() {
        let count = *count as f64;
        norm_b += count * count;
    }
    if norm_a == 0.0 || norm_b == 0.0 {
        0.0
    } else {
        dot / (norm_a.sqrt() * norm_b.sqrt())
    }
}

/// Functions that are shorter than this say nothing by their fingerprint.
const MIN_SIMILAR_INSTRUCTIONS: usize = 8;
/// The least alike a function may be to be listed as similar.
const MIN_SIMILARITY: f64 = 0.95;
const MAX_SIMILAR: usize = 3;

/// A report of the full analysis by the engine: `analysis`, `prototypes`, `cfg` or `fingerprints`.
pub fn engine_report(path: &str, kind: &str) -> Result<String, String> {
    with_cached(path, |cached| {
        let report = intelligence_of(cached)?;
        let mut out = String::new();
        match kind {
            "analysis" => {
                let blocks: usize = report.control_flow_graphs.iter().map(|g| g.blocks.len()).sum();
                let edges: usize = report.control_flow_graphs.iter().map(|g| g.edges.len()).sum();
                let reads: usize = report.data_flow.iter().map(|d| d.memory_reads).sum();
                let writes: usize = report.data_flow.iter().map(|d| d.memory_writes).sum();
                let _ = writeln!(out, "Full analysis by the Ghidra Rust engine");
                let _ = writeln!(out, "functions with a fingerprint: {}", report.fingerprints.len());
                let _ = writeln!(out, "cross references: {}", report.cross_references.len());
                let _ = writeln!(out, "calls: {}", report.call_edges.len());
                let _ = writeln!(out, "control flow: {} blocks and {} edges", blocks, edges);
                let _ = writeln!(out, "data flow: {} memory reads and {} writes", reads, writes);
                let _ = writeln!(out, "prototypes: {}", report.prototypes.len());
                let _ = writeln!(out, "functions that could not be analyzed: {}", report.failures.len());
                let _ = writeln!(out);
                let _ = writeln!(out, "The prototypes and similar functions are now shown at the top of every function.");
                for failure in report.failures.iter().take(200) {
                    let _ = writeln!(out, "{:x} {} ({}): {}", failure.address, failure.name, failure.stage, failure.message);
                }
            }
            "prototypes" => {
                for p in report.prototypes.iter().take(MAX_LINES) {
                    let _ = writeln!(
                        out,
                        "{:x} {}({}) returns {} [{}]",
                        p.address,
                        crate::library::demangle(&p.name),
                        p.parameter_registers.join(", "),
                        bytes_text(&p.return_size_evidence),
                        confidence(p.confidence)
                    );
                    for note in &p.notes {
                        let _ = writeln!(out, "    {}", note);
                    }
                }
            }
            "cfg" => {
                for graph in report.control_flow_graphs.iter().take(MAX_LINES / 10) {
                    let _ = writeln!(out, "function {:x}", graph.function);
                    for block in &graph.blocks {
                        let _ = writeln!(
                            out,
                            "    block {:x}-{:x} ({} instructions)",
                            block.start,
                            block.end,
                            block.instructions.len()
                        );
                    }
                    for edge in &graph.edges {
                        match edge.to {
                            Some(to) => {
                                let _ = writeln!(out, "    {:x} -> {:x} {}", edge.from, to, confidence(edge.kind));
                            }
                            None => {
                                let _ = writeln!(out, "    {:x} -> ? {}", edge.from, confidence(edge.kind));
                            }
                        }
                    }
                }
            }
            "fingerprints" => {
                let mut groups: BTreeMap<u64, Vec<&FunctionFingerprint>> = BTreeMap::new();
                for f in &report.fingerprints {
                    if f.instruction_count >= MIN_SIMILAR_INSTRUCTIONS {
                        groups.entry(f.byte_hash).or_default().push(f);
                    }
                }
                let _ = writeln!(out, "Functions with the same code");
                let mut found = false;
                for (hash, group) in groups.iter().filter(|(_, g)| g.len() > 1) {
                    found = true;
                    let _ = writeln!(out, "{:016x}", hash);
                    for f in group {
                        let _ = writeln!(out, "    {:x} {}", f.address, crate::library::demangle(&f.name));
                    }
                }
                if !found {
                    let _ = writeln!(out, "none");
                }
                let _ = writeln!(out);
                let _ = writeln!(out, "All fingerprints: address, name, instructions, operations, hash");
                for f in report.fingerprints.iter().take(MAX_LINES) {
                    let _ = writeln!(
                        out,
                        "{:x} {} {} {} {:016x}",
                        f.address,
                        crate::library::demangle(&f.name),
                        f.instruction_count,
                        f.operation_count,
                        f.byte_hash
                    );
                }
            }
            other => return Err(format!("Unknown report {}", other)),
        }
        if out.is_empty() {
            out.push_str("The engine found nothing in this library\n");
        }
        Ok(out)
    })
}

/// Comment lines about the function at `address` from the full analysis: its prototype, control flow and
/// similar functions. Empty until the full analysis was made for the open library. Every line starts with `comment`.
pub fn insights(path: &str, address: u64, comment: &str) -> Result<String, String> {
    let text = with_open(path, |cached| {
        let report = cached.intelligence.as_ref()?;
        let mut out = String::new();
        if let Some(p) = report.prototypes.iter().find(|p| p.address == address) {
            let _ = writeln!(
                out,
                "{}prototype: {}({}) returns {} [{}]",
                comment,
                crate::library::demangle(&p.name),
                p.parameter_registers.join(", "),
                bytes_text(&p.return_size_evidence),
                confidence(p.confidence)
            );
        }
        if let Some(graph) = report.control_flow_graphs.iter().find(|g| g.function == address) {
            let _ = writeln!(out, "{}control flow: {} blocks, {} edges", comment, graph.blocks.len(), graph.edges.len());
        }
        if let Some(target) = report.fingerprints.iter().find(|f| f.address == address) {
            if target.instruction_count >= MIN_SIMILAR_INSTRUCTIONS {
                let mut similar: Vec<(f64, &FunctionFingerprint)> = report
                    .fingerprints
                    .iter()
                    .filter(|f| f.address != address && f.instruction_count >= MIN_SIMILAR_INSTRUCTIONS)
                    .map(|f| (similarity(target, f), f))
                    .filter(|(score, _)| *score >= MIN_SIMILARITY)
                    .collect();
                similar.sort_by(|a, b| b.0.total_cmp(&a.0));
                let names: Vec<String> = similar
                    .iter()
                    .take(MAX_SIMILAR)
                    .map(|(score, f)| format!("{} ({:.0}%)", crate::library::demangle(&f.name), score * 100.0))
                    .collect();
                if !names.is_empty() {
                    let _ = writeln!(out, "{}similar functions: {}", comment, names.join(", "));
                }
            }
        }
        Some(out)
    });
    Ok(text.flatten().unwrap_or_default())
}
