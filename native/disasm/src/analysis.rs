// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Cross references and call graph of an ARM64 library: who calls a function, what it calls, which
//! strings it uses, and the imports and exports of the library.

use std::collections::{BTreeMap, HashMap, HashSet};
use std::fmt::Write;
use std::sync::Mutex;
use std::time::SystemTime;

use object::{Architecture, Object, ObjectSection, ObjectSymbol, SectionKind};

use crate::library::{demangle, functions_of, symbolize, Func, Library};
use crate::pseudo::{string_table, Strings};

/// The most lines of a report.
const MAX_LINES: usize = 300_000;
/// The most names in one line of a function header.
const MAX_LISTED: usize = 12;
/// The most characters of a string in a function header.
const MAX_STRING_SHOWN: usize = 40;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Kind {
    /// `bl`
    Call,
    /// `b`, a jump to the start of another function is a tail call
    Jump,
    /// The address is built from `adrp`, `adr` or a literal load
    Data,
}

impl Kind {
    fn label(self) -> &'static str {
        match self {
            Kind::Call => "call",
            Kind::Jump => "jump",
            Kind::Data => "data",
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Xref {
    pub from: u64,
    pub to: u64,
    pub kind: Kind,
}

/// What was found in the code of a library.
pub struct Analysis {
    labels: BTreeMap<u64, Func>,
    /// Sorted by the address they point to.
    refs: Vec<Xref>,
    /// Indexes of `refs`, sorted by the address they come from.
    from_index: Vec<u32>,
    strings: Strings,
    imports: Vec<String>,
    /// Address, size, name and kind.
    exports: Vec<(u64, u64, String, String)>,
    /// The GOT slots of the imported functions by address.
    got: BTreeMap<u64, String>,
}

fn sext(value: u32, bits: u32) -> i64 {
    ((value as i64) << (64 - bits)) >> (64 - bits)
}

/// Finds the references in the code: calls, jumps and addresses built from `adrp`, `adr` and literal loads.
fn sweep(base: u64, data: &[u8], refs: &mut Vec<Xref>) {
    let mut pages: [Option<u64>; 32] = [None; 32];
    for (i, bytes) in data.chunks_exact(4).enumerate() {
        let pc = base.wrapping_add((i as u64) * 4);
        let w = u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]);
        if (w & 0xfc00_0000) == 0x9400_0000 || (w & 0xfc00_0000) == 0x1400_0000 {
            let kind = if (w >> 31) == 1 { Kind::Call } else { Kind::Jump };
            let to = pc.wrapping_add((sext(w & 0x03ff_ffff, 26) * 4) as u64);
            refs.push(Xref { from: pc, to, kind });
            if kind == Kind::Jump {
                pages = [None; 32];
            }
        } else if w == 0xd65f_03c0 {
            pages = [None; 32];
        } else if (w & 0x9f00_0000) == 0x9000_0000 {
            let rd = (w & 0x1f) as usize;
            let imm = sext((((w >> 5) & 0x7_ffff) << 2) | ((w >> 29) & 3), 21);
            pages[rd] = Some(((pc & !0xfff) as i64).wrapping_add(imm << 12) as u64);
        } else if (w & 0x9f00_0000) == 0x1000_0000 {
            let imm = sext((((w >> 5) & 0x7_ffff) << 2) | ((w >> 29) & 3), 21);
            refs.push(Xref { from: pc, to: pc.wrapping_add(imm as u64), kind: Kind::Data });
        } else if (w & 0xff80_0000) == 0x9100_0000 {
            let rd = (w & 0x1f) as usize;
            let rn = ((w >> 5) & 0x1f) as usize;
            let imm = ((w >> 10) & 0xfff) as u64;
            let shift = if ((w >> 22) & 1) == 1 { 12 } else { 0 };
            if let Some(page) = pages[rn] {
                refs.push(Xref { from: pc, to: page.wrapping_add(imm << shift), kind: Kind::Data });
            }
            pages[rd] = None;
        } else if (w & 0x3f00_0000) == 0x3900_0000 {
            let rt = (w & 0x1f) as usize;
            let rn = ((w >> 5) & 0x1f) as usize;
            if let Some(page) = pages[rn] {
                let size = (w >> 30) & 3;
                let offset = (((w >> 10) & 0xfff) as u64) << size;
                refs.push(Xref { from: pc, to: page.wrapping_add(offset), kind: Kind::Data });
            }
            if ((w >> 22) & 3) != 0 {
                pages[rt] = None;
            }
        } else if (w & 0x3b00_0000) == 0x1800_0000 {
            let offset = sext((w >> 5) & 0x7_ffff, 19) * 4;
            refs.push(Xref { from: pc, to: pc.wrapping_add(offset as u64), kind: Kind::Data });
        }
    }
}

fn short(text: &str) -> String {
    let mut out: String = text.chars().filter(|c| !c.is_control()).take(MAX_STRING_SHOWN).collect();
    if text.chars().count() > MAX_STRING_SHOWN {
        out.push_str("...");
    }
    out
}

fn limited(items: &[String]) -> String {
    let mut text = items.iter().take(MAX_LISTED).cloned().collect::<Vec<String>>().join(", ");
    if items.len() > MAX_LISTED {
        let _ = write!(text, ", ... {} more", items.len() - MAX_LISTED);
    }
    text
}

impl Analysis {
    fn new(
        labels: BTreeMap<u64, Func>,
        mut refs: Vec<Xref>,
        strings: Strings,
        imports: Vec<String>,
        exports: Vec<(u64, u64, String, String)>,
        got: BTreeMap<u64, String>,
    ) -> Analysis {
        refs.sort_by_key(|r| (r.to, r.from));
        refs.dedup();
        let mut from_index: Vec<u32> = (0..refs.len() as u32).collect();
        from_index.sort_by_key(|&i| refs[i as usize].from);
        Analysis { labels, refs, from_index, strings, imports, exports, got }
    }

    /// The references to an address.
    pub fn refs_to(&self, address: u64) -> &[Xref] {
        let start = self.refs.partition_point(|r| r.to < address);
        let end = self.refs.partition_point(|r| r.to <= address);
        &self.refs[start..end]
    }

    /// The references that come from the addresses `start` up to `end`.
    pub fn refs_from(&self, start: u64, end: u64) -> impl Iterator<Item = &Xref> + '_ {
        let low = self.from_index.partition_point(|&i| self.refs[i as usize].from < start);
        let high = self.from_index.partition_point(|&i| self.refs[i as usize].from < end);
        self.from_index[low..high].iter().map(move |&i| &self.refs[i as usize])
    }

    /// `name` or `name+0x10` for an address, or the address itself.
    fn name_at(&self, address: u64) -> String {
        symbolize(&self.labels, address).unwrap_or_else(|| format!("0x{:x}", address))
    }

    /// The name of the function that contains an address, without the offset.
    fn function_name(&self, address: u64) -> String {
        match self.labels.range(..=address).next_back() {
            Some((_, f)) if address < f.addr + f.size.max(1) => demangle(&f.name),
            _ => format!("0x{:x}", address),
        }
    }

    /// Whether an address is in a stub of the PLT.
    fn in_plt(&self, address: u64) -> bool {
        matches!(
            self.labels.range(..=address).next_back(),
            Some((_, f)) if f.name.ends_with("@plt") && address < f.addr + f.size.max(1)
        )
    }

    /// How often the code outside of the PLT points to a GOT slot.
    fn got_uses(&self, address: u64) -> usize {
        self.refs_to(address).iter().filter(|r| !self.in_plt(r.from)).count()
    }

    /// The functions that a function calls or jumps to. Imported functions that are reached through
    /// the GOT are included.
    fn callees(&self, func: &Func) -> Vec<String> {
        if func.name.ends_with("@plt") {
            return Vec::new();
        }
        let end = func.addr + func.size;
        let mut seen = HashSet::new();
        let mut names = Vec::new();
        for r in self.refs_from(func.addr, end) {
            if r.kind == Kind::Data {
                if let Some(name) = self.got.get(&r.to) {
                    if seen.insert(name.clone()) {
                        names.push(name.clone());
                    }
                }
                continue;
            }
            if r.kind == Kind::Jump && (!self.labels.contains_key(&r.to) || (r.to >= func.addr && r.to < end)) {
                continue;
            }
            if let Some(name) = symbolize(&self.labels, r.to) {
                if seen.insert(name.clone()) {
                    names.push(name);
                }
            }
        }
        names
    }

    /// The strings that a function points to.
    fn string_refs(&self, func: &Func) -> Vec<String> {
        let mut seen = HashSet::new();
        let mut texts = Vec::new();
        for r in self.refs_from(func.addr, func.addr + func.size) {
            if r.kind != Kind::Data {
                continue;
            }
            if let Some(text) = self.strings.get(&r.to) {
                if seen.insert(r.to) {
                    texts.push(format!("\"{}\"", short(text)));
                }
            }
        }
        texts
    }

    /// Comment lines for the top of a function: who uses it, what it calls and which strings it points to.
    /// Every line starts with `comment`.
    pub fn function_header(&self, address: u64, comment: &str) -> String {
        let Some(func) = self.labels.get(&address) else { return String::new() };
        let end = func.addr + func.size;
        let mut seen = HashSet::new();
        let mut callers: Vec<String> = Vec::new();
        for r in self.refs_to(address) {
            let inside = r.from >= func.addr && r.from < end;
            if r.kind == Kind::Jump && inside {
                continue;
            }
            let name = self.function_name(r.from);
            let label = match r.kind {
                Kind::Call => name,
                Kind::Jump => format!("{} (tail call)", name),
                Kind::Data => format!("{} (address)", name),
            };
            if seen.insert(label.clone()) {
                callers.push(label);
            }
        }
        let callees = self.callees(func);
        let strings = self.string_refs(func);
        let mut out = String::new();
        if !callers.is_empty() {
            let _ = writeln!(out, "{}xrefs to this function ({}): {}", comment, callers.len(), limited(&callers));
        }
        if !callees.is_empty() {
            let _ = writeln!(out, "{}calls ({}): {}", comment, callees.len(), limited(&callees));
        }
        if !strings.is_empty() {
            let _ = writeln!(out, "{}strings ({}): {}", comment, strings.len(), limited(&strings));
        }
        out
    }

    /// A report about the whole library: imports, exports, xrefs or callgraph.
    pub fn report(&self, kind: &str) -> Result<String, String> {
        match kind {
            "imports" => Ok(self.imports_text()),
            "exports" => Ok(self.exports_text()),
            "xrefs" => Ok(self.xrefs_text()),
            "callgraph" => Ok(self.callgraph_text()),
            _ => Err(format!("Unknown report {}", kind)),
        }
    }

    fn imports_text(&self) -> String {
        let mut plt: HashMap<&str, u64> = HashMap::new();
        for f in self.labels.values() {
            if let Some(base) = f.name.strip_suffix("@plt") {
                plt.insert(base, f.addr);
            }
        }
        let mut got_used: HashMap<&str, usize> = HashMap::new();
        for (address, name) in &self.got {
            *got_used.entry(name.as_str()).or_insert(0) += self.got_uses(*address);
        }
        let mut names = self.imports.clone();
        names.sort();
        names.dedup();
        let mut out = String::new();
        for name in &names {
            let used = plt.get(name.as_str()).map(|a| self.refs_to(*a).len()).unwrap_or(0)
                + got_used.get(name.as_str()).copied().unwrap_or(0);
            let readable = demangle(name);
            let shown = if readable != *name { format!("{}  ({})", name, readable) } else { name.clone() };
            if used > 0 {
                let _ = writeln!(out, "{}  [{} references]", shown, used);
            } else {
                let _ = writeln!(out, "{}", shown);
            }
        }
        if out.is_empty() {
            out.push_str("No imported symbols\n");
        }
        out
    }

    fn exports_text(&self) -> String {
        let mut list = self.exports.clone();
        list.sort();
        let mut out = String::new();
        for (address, size, name, kind) in &list {
            let readable = demangle(name);
            if readable != *name {
                let _ = writeln!(out, "{:016x} {:>8} {:<5} {}  ({})", address, size, kind, name, readable);
            } else {
                let _ = writeln!(out, "{:016x} {:>8} {:<5} {}", address, size, kind, name);
            }
        }
        if out.is_empty() {
            out.push_str("No exported symbols\n");
        }
        out
    }

    fn xrefs_text(&self) -> String {
        let mut out = String::from("== Functions ==\n");
        let mut lines = 0usize;
        for f in self.labels.values() {
            let list = self.refs_to(f.addr);
            if list.is_empty() {
                continue;
            }
            let _ = writeln!(out, "{}  {:x}", demangle(&f.name), f.addr);
            lines += 1;
            for r in list {
                let _ = writeln!(out, "  from {}  {}", self.name_at(r.from), r.kind.label());
                lines += 1;
                if lines >= MAX_LINES {
                    out.push_str("... more references were not listed\n");
                    return out;
                }
            }
        }
        out.push_str("\n== Strings ==\n");
        let mut addresses: Vec<u64> = self.strings.keys().copied().collect();
        addresses.sort_unstable();
        for address in addresses {
            let list = self.refs_to(address);
            if list.is_empty() {
                continue;
            }
            let _ = writeln!(out, "{:x}  \"{}\"", address, short(&self.strings[&address]));
            lines += 1;
            for r in list {
                let _ = writeln!(out, "  from {}  {}", self.name_at(r.from), r.kind.label());
                lines += 1;
                if lines >= MAX_LINES {
                    out.push_str("... more references were not listed\n");
                    return out;
                }
            }
        }
        out
    }

    fn callgraph_text(&self) -> String {
        let mut out = String::new();
        let mut lines = 0usize;
        for f in self.labels.values() {
            let callees = self.callees(f);
            if callees.is_empty() {
                continue;
            }
            let _ = writeln!(out, "{} -> {}", demangle(&f.name), callees.join(", "));
            lines += 1;
            if lines >= MAX_LINES {
                out.push_str("... more functions were not listed\n");
                break;
            }
        }
        if out.is_empty() {
            out.push_str("No calls found\n");
        }
        out
    }
}

impl Library {
    /// Reads all the code of an ARM64 library for references.
    pub fn analyze(&self) -> Result<Analysis, String> {
        let file = self.file()?;
        if file.architecture() != Architecture::Aarch64 {
            return Err(format!("{:?} is not supported yet, only ARM64 can be analyzed", file.architecture()));
        }
        let labels: BTreeMap<u64, Func> = functions_of(&file).into_iter().map(|f| (f.addr, f)).collect();
        let ranges: Vec<(u64, u64)> = file
            .sections()
            .map(|s| (s.address(), s.address() + s.size()))
            .filter(|(start, end)| start < end)
            .collect();
        let mut found = Vec::new();
        for section in file.sections() {
            if section.kind() != SectionKind::Text || section.size() == 0 {
                continue;
            }
            let Ok(data) = section.data() else { continue };
            sweep(section.address(), data, &mut found);
        }
        let refs: Vec<Xref> = found
            .into_iter()
            .filter(|r| ranges.iter().any(|(start, end)| r.to >= *start && r.to < *end))
            .collect();
        let mut imports = Vec::new();
        let mut exports = Vec::new();
        for sym in file.dynamic_symbols() {
            let Ok(name) = sym.name() else { continue };
            if name.is_empty() {
                continue;
            }
            if sym.is_undefined() {
                imports.push(name.to_string());
            } else if sym.is_global() && sym.address() != 0 {
                exports.push((sym.address(), sym.size(), name.to_string(), format!("{:?}", sym.kind()).to_lowercase()));
            }
        }
        let mut symbol_names: HashMap<usize, String> = HashMap::new();
        for sym in file.dynamic_symbols() {
            if let Ok(name) = sym.name() {
                symbol_names.insert(sym.index().0, name.to_string());
            }
        }
        let mut got = BTreeMap::new();
        if let Some(relocations) = file.dynamic_relocations() {
            for (offset, relocation) in relocations {
                if let object::RelocationTarget::Symbol(index) = relocation.target() {
                    if let Some(name) = symbol_names.get(&index.0) {
                        if !name.is_empty() {
                            got.insert(offset, name.clone());
                        }
                    }
                }
            }
        }
        let strings = string_table(&self.strings_text()?);
        Ok(Analysis::new(labels, refs, strings, imports, exports, got))
    }
}

struct Entry {
    path: String,
    length: u64,
    modified: Option<SystemTime>,
    analysis: Analysis,
}

/// The last library that was analyzed, so that the next function header does not read it again.
static CACHE: Mutex<Option<Entry>> = Mutex::new(None);

fn with<T>(path: &str, work: impl FnOnce(&Analysis) -> T) -> Result<T, String> {
    let meta = std::fs::metadata(path).map_err(|e| format!("Cannot read {}: {}", path, e))?;
    let length = meta.len();
    let modified = meta.modified().ok();
    let mut guard = CACHE.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
    let fresh = matches!(&*guard, Some(e) if e.path == path && e.length == length && e.modified == modified);
    if !fresh {
        *guard = None;
        let analysis = Library::open(path)?.analyze()?;
        *guard = Some(Entry { path: path.to_string(), length, modified, analysis });
    }
    let entry = guard.as_ref().ok_or_else(|| "No library is open".to_string())?;
    Ok(work(&entry.analysis))
}

/// A report about the library at `path`, see [`Analysis::report`].
pub fn report(path: &str, kind: &str) -> Result<String, String> {
    with(path, |analysis| analysis.report(kind))?
}

/// Comment lines for the function at `address`, see [`Analysis::function_header`].
pub fn function_header(path: &str, address: u64, comment: &str) -> Result<String, String> {
    with(path, |analysis| analysis.function_header(address, comment))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn bytes(words: &[u32]) -> Vec<u8> {
        words.iter().flat_map(|w| w.to_le_bytes()).collect()
    }

    fn found(base: u64, words: &[u32]) -> Vec<Xref> {
        let mut refs = Vec::new();
        sweep(base, &bytes(words), &mut refs);
        refs
    }

    #[test]
    fn calls_and_jumps() {
        let refs = found(0x1000, &[0x9400_0004, 0x17ff_fffe]);
        assert_eq!(refs[0], Xref { from: 0x1000, to: 0x1010, kind: Kind::Call });
        assert_eq!(refs[1], Xref { from: 0x1004, to: 0xffc, kind: Kind::Jump });
    }

    #[test]
    fn adrp_and_add() {
        // adrp x0 gives the page 0x2000 at 0x1234, add x0, x0, #0x10
        let refs = found(0x1234, &[0xb000_0000, 0x9100_4000]);
        assert_eq!(refs, vec![Xref { from: 0x1238, to: 0x2010, kind: Kind::Data }]);
    }

    #[test]
    fn adrp_and_load() {
        // adrp x0, ldr x1, [x0, #8]
        let refs = found(0x1234, &[0xb000_0000, 0xf940_0401]);
        assert_eq!(refs, vec![Xref { from: 0x1238, to: 0x2008, kind: Kind::Data }]);
    }

    #[test]
    fn return_forgets_the_page() {
        let refs = found(0x1234, &[0xb000_0000, 0xd65f_03c0, 0x9100_4000]);
        assert!(refs.is_empty());
    }

    #[test]
    fn adr_and_literal_loads() {
        assert_eq!(found(0x100, &[0x1000_0040]), vec![Xref { from: 0x100, to: 0x108, kind: Kind::Data }]);
        assert_eq!(found(0x100, &[0x5800_0100]), vec![Xref { from: 0x100, to: 0x120, kind: Kind::Data }]);
    }

    fn sample() -> Analysis {
        let mut labels = BTreeMap::new();
        labels.insert(0x1000, Func { addr: 0x1000, size: 0x20, name: "caller".to_string() });
        labels.insert(0x2000, Func { addr: 0x2000, size: 0x10, name: "callee".to_string() });
        let refs = vec![
            Xref { from: 0x1008, to: 0x2000, kind: Kind::Call },
            Xref { from: 0x1010, to: 0x5000, kind: Kind::Data },
            Xref { from: 0x1018, to: 0x6000, kind: Kind::Data },
        ];
        let mut strings = Strings::new();
        strings.insert(0x5000, "hello".to_string());
        let exports = vec![(0x2000, 0x10, "callee".to_string(), "text".to_string())];
        let mut got = BTreeMap::new();
        got.insert(0x6000, "malloc".to_string());
        Analysis::new(labels, refs, strings, vec!["malloc".to_string()], exports, got)
    }

    #[test]
    fn function_headers() {
        let a = sample();
        assert_eq!(a.function_header(0x2000, "// "), "// xrefs to this function (1): caller\n");
        assert_eq!(a.function_header(0x1000, "; "), "; calls (2): callee, malloc\n; strings (1): \"hello\"\n");
        assert_eq!(a.function_header(0x3000, "; "), "");
    }

    #[test]
    fn reports() {
        let a = sample();
        assert_eq!(a.report("imports").unwrap(), "malloc  [1 references]\n");
        assert!(a.report("exports").unwrap().contains("callee"));
        let xrefs = a.report("xrefs").unwrap();
        assert!(xrefs.contains("callee  2000"), "{}", xrefs);
        assert!(xrefs.contains("  from caller+0x8  call"), "{}", xrefs);
        assert!(xrefs.contains("5000  \"hello\""), "{}", xrefs);
        assert_eq!(a.report("callgraph").unwrap(), "caller -> callee, malloc\n");
        assert!(a.report("nothing").is_err());
    }
}
