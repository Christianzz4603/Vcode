// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Reads a native library and produces the texts that are shown in the editor.

use std::collections::{BTreeMap, HashMap};
use std::fmt::Write;

use object::{Architecture, Object, ObjectSection, ObjectSymbol, SectionKind, SymbolKind};

use crate::arm64;

const CHUNK: u64 = 0x4000;
const MAX_STRINGS: usize = 300_000;
const MAX_FILE: u64 = 512 * 1024 * 1024;

#[derive(Clone, Debug)]
pub struct Func {
    pub addr: u64,
    pub size: u64,
    pub name: String,
}

pub struct Library {
    data: Vec<u8>,
}

/// The readable form of a symbol name.
pub fn demangle(name: &str) -> String {
    if let Ok(d) = rustc_demangle::try_demangle(name) {
        return format!("{:#}", d);
    }
    if name.starts_with("_Z") {
        if let Ok(symbol) = cpp_demangle::Symbol::new(name) {
            return symbol.to_string();
        }
    }
    name.to_string()
}

impl Library {
    pub fn open(path: &str) -> Result<Library, String> {
        let size = std::fs::metadata(path).map_err(|e| format!("Cannot read {}: {}", path, e))?.len();
        if size > MAX_FILE {
            return Err("The library is too big".to_string());
        }
        let data = std::fs::read(path).map_err(|e| format!("Cannot read {}: {}", path, e))?;
        object::File::parse(&*data).map_err(|e| format!("This is not a supported library: {}", e))?;
        Ok(Library { data })
    }

    fn file(&self) -> Result<object::File<'_>, String> {
        object::File::parse(&*self.data).map_err(|e| e.to_string())
    }

    pub fn info(&self) -> Result<String, String> {
        let file = self.file()?;
        let mut out = String::new();
        let _ = writeln!(out, "Size: {} bytes", self.data.len());
        let _ = writeln!(
            out,
            "Format: {:?}, architecture: {:?}, {} bit, {}-endian, kind: {:?}",
            file.format(),
            file.architecture(),
            if file.is_64() { 64 } else { 32 },
            if file.is_little_endian() { "little" } else { "big" },
            file.kind()
        );
        let _ = writeln!(out, "Entry point: 0x{:x}", file.entry());
        if let Ok(Some(id)) = file.build_id() {
            let hex: String = id.iter().map(|b| format!("{:02x}", b)).collect();
            let _ = writeln!(out, "Build ID: {}", hex);
        }
        if file.architecture() != Architecture::Aarch64 {
            let _ = writeln!(out, "Disassembly: only ARM64 is supported for now, the code of this library cannot be shown");
        }
        let _ = writeln!(out, "\nSections:");
        let _ = writeln!(out, "{:<24} {:>16} {:>10}  kind", "name", "address", "size");
        for section in file.sections() {
            let _ = writeln!(
                out,
                "{:<24} {:>16x} {:>10x}  {:?}",
                section.name().unwrap_or("?"),
                section.address(),
                section.size(),
                section.kind()
            );
        }
        let funcs = functions_of(&file);
        let _ = writeln!(
            out,
            "\n{} functions, {} imported symbols",
            funcs.len(),
            file.dynamic_symbols().filter(|s| s.is_undefined()).count()
        );
        Ok(out)
    }

    pub fn functions(&self) -> Result<Vec<Func>, String> {
        let file = self.file()?;
        Ok(functions_of(&file))
    }

    /// One line per symbol.
    pub fn symbols_text(&self) -> Result<String, String> {
        let file = self.file()?;
        let mut out = String::new();
        let _ = writeln!(out, "{:>16} {:>8}  {:<8} name", "address", "size", "kind");
        let tables = [
            ("symtab", file.symbols().collect::<Vec<_>>()),
            ("dynsym", file.dynamic_symbols().collect::<Vec<_>>()),
        ];
        for (table, symbols) in tables {
            if symbols.is_empty() {
                continue;
            }
            let _ = writeln!(out, "\n# {}", table);
            for sym in symbols {
                let name = sym.name().unwrap_or("");
                if name.is_empty() {
                    continue;
                }
                let kind = if sym.is_undefined() { "undef".to_string() } else { format!("{:?}", sym.kind()).to_lowercase() };
                let readable = demangle(name);
                if readable != name {
                    let _ = writeln!(out, "{:>16x} {:>8}  {:<8} {}  ({})", sym.address(), sym.size(), kind, name, readable);
                } else {
                    let _ = writeln!(out, "{:>16x} {:>8}  {:<8} {}", sym.address(), sym.size(), kind, name);
                }
            }
        }
        Ok(out)
    }

    /// Text strings in the read-only data.
    pub fn strings_text(&self) -> Result<String, String> {
        let file = self.file()?;
        let mut out = String::new();
        let mut count = 0usize;
        for section in file.sections() {
            if !matches!(section.kind(), SectionKind::ReadOnlyString | SectionKind::ReadOnlyData) {
                continue;
            }
            let Ok(data) = section.data() else { continue };
            let base = section.address();
            let name = section.name().unwrap_or("?");
            let mut start = 0usize;
            while start < data.len() {
                if !is_text_byte(data[start]) {
                    start += 1;
                    continue;
                }
                let mut end = start;
                while end < data.len() && is_text_byte(data[end]) {
                    end += 1;
                }
                if end - start >= 4 {
                    let text = String::from_utf8_lossy(&data[start..end]);
                    let _ = writeln!(out, "{:08x}  {:<10} {}", base + start as u64, name, text);
                    count += 1;
                    if count >= MAX_STRINGS {
                        let _ = writeln!(out, "... more strings were not listed");
                        return Ok(out);
                    }
                }
                start = end + 1;
            }
        }
        if count == 0 {
            out.push_str("No strings found\n");
        }
        Ok(out)
    }

    /// Code sections cut in pieces: (start, end, section name).
    pub fn chunks(&self) -> Result<Vec<(u64, u64, String)>, String> {
        let file = self.file()?;
        let mut out = Vec::new();
        for section in file.sections() {
            if section.kind() != SectionKind::Text || section.size() == 0 {
                continue;
            }
            let name = section.name().unwrap_or("text").trim_start_matches('.').to_string();
            let mut start = section.address();
            let end = section.address() + section.size();
            while start < end {
                let stop = (start + CHUNK).min(end);
                out.push((start, stop, name.clone()));
                start = stop;
            }
        }
        Ok(out)
    }

    /// Disassembles the addresses from `start` up to `end`.
    pub fn disassemble(&self, start: u64, end: u64) -> Result<String, String> {
        let file = self.file()?;
        if file.architecture() != Architecture::Aarch64 {
            return Err(format!("{:?} is not supported yet, only ARM64 can be disassembled", file.architecture()));
        }
        let section = file
            .sections()
            .find(|s| s.kind() == SectionKind::Text && start >= s.address() && start < s.address() + s.size())
            .ok_or_else(|| "The address is not in a code section".to_string())?;
        let data = section.data().map_err(|e| e.to_string())?;
        let base = section.address();
        let end = end.min(base + data.len() as u64);
        let labels: BTreeMap<u64, Func> = functions_of(&file).into_iter().map(|f| (f.addr, f)).collect();
        let decoder = arm64::decoder();
        let mut out = String::new();
        let mut pc = start & !3;
        while pc < end {
            let offset = (pc - base) as usize;
            if offset + 4 > data.len() {
                break;
            }
            let word = u32::from_le_bytes([data[offset], data[offset + 1], data[offset + 2], data[offset + 3]]);
            if let Some(func) = labels.get(&pc) {
                let _ = write!(out, "\n{}:\n", demangle(&func.name));
            }
            let (text, target) = arm64::decode(&decoder, word, pc).unwrap_or_else(|| (format!(".inst 0x{:08x}", word), None));
            let note = target
                .and_then(|t| symbolize(&labels, t))
                .map(|s| format!("  ; <{}>", s))
                .unwrap_or_default();
            let _ = writeln!(out, "{:08x}:  {:08x}  {}{}", pc, word, text, note);
            pc += 4;
        }
        Ok(out)
    }
}

fn is_text_byte(b: u8) -> bool {
    (0x20..0x7f).contains(&b) || b == b'\t'
}

fn symbolize(labels: &BTreeMap<u64, Func>, target: u64) -> Option<String> {
    let (addr, func) = labels.range(..=target).next_back()?;
    let offset = target - addr;
    if offset == 0 {
        Some(demangle(&func.name))
    } else if offset < func.size {
        Some(format!("{}+0x{:x}", demangle(&func.name), offset))
    } else {
        None
    }
}

fn functions_of(file: &object::File<'_>) -> Vec<Func> {
    let mut map: BTreeMap<u64, Func> = BTreeMap::new();
    for sym in file.symbols().chain(file.dynamic_symbols()) {
        if sym.kind() != SymbolKind::Text || sym.is_undefined() {
            continue;
        }
        let addr = sym.address();
        if addr == 0 {
            continue;
        }
        let name = match sym.name() {
            Ok(n) if !n.is_empty() => n.to_string(),
            _ => continue,
        };
        let size = sym.size();
        map.entry(addr)
            .and_modify(|f| {
                if f.size == 0 && size > 0 {
                    f.size = size;
                }
            })
            .or_insert(Func { addr, size, name });
    }
    for (addr, name) in plt_labels(file) {
        map.entry(addr).or_insert(Func { addr, size: 16, name });
    }
    let addrs: Vec<u64> = map.keys().copied().collect();
    for (i, addr) in addrs.iter().enumerate() {
        if map[addr].size != 0 {
            continue;
        }
        let section_end = file
            .sections()
            .find(|s| s.kind() == SectionKind::Text && *addr >= s.address() && *addr < s.address() + s.size())
            .map(|s| s.address() + s.size());
        let next = addrs.get(i + 1).copied();
        let limit = match (next, section_end) {
            (Some(n), Some(e)) => n.min(e),
            (Some(n), None) => n,
            (None, Some(e)) => e,
            (None, None) => *addr,
        };
        if let Some(f) = map.get_mut(addr) {
            f.size = limit.saturating_sub(*addr);
        }
    }
    map.into_values().collect()
}

/// Names for the stubs in the PLT, found through the dynamic relocations.
fn plt_labels(file: &object::File<'_>) -> BTreeMap<u64, String> {
    let mut labels = BTreeMap::new();
    if file.architecture() != Architecture::Aarch64 {
        return labels;
    }
    let mut dynamic_names: HashMap<usize, String> = HashMap::new();
    for sym in file.dynamic_symbols() {
        if let Ok(name) = sym.name() {
            dynamic_names.insert(sym.index().0, name.to_string());
        }
    }
    let mut got_names: HashMap<u64, String> = HashMap::new();
    if let Some(relocations) = file.dynamic_relocations() {
        for (offset, relocation) in relocations {
            if let object::RelocationTarget::Symbol(index) = relocation.target() {
                if let Some(name) = dynamic_names.get(&index.0) {
                    if !name.is_empty() {
                        got_names.insert(offset, name.clone());
                    }
                }
            }
        }
    }
    if got_names.is_empty() {
        return labels;
    }
    for section in file.sections() {
        let name = section.name().unwrap_or("");
        if !name.starts_with(".plt") {
            continue;
        }
        let Ok(data) = section.data() else { continue };
        let words: Vec<u32> = data.chunks_exact(4).map(|c| u32::from_le_bytes([c[0], c[1], c[2], c[3]])).collect();
        for i in 0..words.len().saturating_sub(1) {
            let pc = section.address() + (i as u64) * 4;
            if let Some(got) = arm64::plt_got_address(words[i], words[i + 1], pc) {
                if let Some(symbol) = got_names.get(&got) {
                    // A stub can start with bti c
                    let start = if i > 0 && words[i - 1] == 0xd503_245f { pc - 4 } else { pc };
                    labels.insert(start, format!("{}@plt", symbol));
                }
            }
        }
    }
    labels
}
