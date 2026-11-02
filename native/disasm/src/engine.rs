// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Cross references and the call graph from the Rust Ghidra engine. They work for every architecture the engine
//! reads, so they are used where the ARM64 analysis of [`crate::analysis`] cannot help.

use std::collections::{BTreeMap, BTreeSet};
use std::fmt::Write;

use crate::decompile::{graph_of, with_cached, Graph};

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
