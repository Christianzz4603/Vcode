// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Symbols that Windows tools write for a library: the `.pdb` file of a `.dll` or `.exe`.

use std::path::{Path, PathBuf};

use pdb::FallibleIterator;

/// The most symbols that are read from one `.pdb` file.
const MAX_SYMBOLS: usize = 500_000;

/// The `.pdb` file of the library: the one with the same name next to it, or the only `.pdb` in its folder.
pub fn find_pdb(library: &Path) -> Option<PathBuf> {
    let folder = library.parent()?;
    let own = library.with_extension("pdb");
    if own.is_file() {
        return Some(own);
    }
    let mut found: Vec<PathBuf> = std::fs::read_dir(folder)
        .ok()?
        .filter_map(|entry| entry.ok().map(|e| e.path()))
        .filter(|p| p.extension().map(|e| e.eq_ignore_ascii_case("pdb")).unwrap_or(false))
        .collect();
    if found.len() == 1 {
        found.pop()
    } else {
        None
    }
}

/// The address a PE library wants to be loaded at, `None` when the file is not a PE file.
pub fn image_base(library: &Path) -> Option<u64> {
    use std::io::Read;
    let mut head = Vec::new();
    std::fs::File::open(library).ok()?.take(4096).read_to_end(&mut head).ok()?;
    if head.get(0..2)? != b"MZ" {
        return None;
    }
    let pe = u32::from_le_bytes(head.get(0x3c..0x40)?.try_into().ok()?) as usize;
    if head.get(pe..pe + 4)? != b"PE\0\0" {
        return None;
    }
    let optional = pe + 24;
    match u16::from_le_bytes(head.get(optional..optional + 2)?.try_into().ok()?) {
        0x10b => Some(u32::from_le_bytes(head.get(optional + 28..optional + 32)?.try_into().ok()?) as u64),
        0x20b => Some(u64::from_le_bytes(head.get(optional + 24..optional + 32)?.try_into().ok()?)),
        _ => None,
    }
}

/// A readable name of a Windows symbol, which may be decorated by the Microsoft C++ compiler.
pub fn demangle(name: &str) -> String {
    if name.starts_with('?') {
        if let Ok(readable) = msvc_demangler::demangle(name, msvc_demangler::DemangleFlags::llvm()) {
            return readable;
        }
    }
    name.to_string()
}

/// The functions of a `.pdb` file as the offset from the image base and the name, sorted by offset.
pub fn read_symbols(pdb_path: &Path) -> Result<Vec<(u32, String)>, String> {
    let file = std::fs::File::open(pdb_path).map_err(|e| format!("Cannot read {}: {}", pdb_path.display(), e))?;
    let mut pdb = pdb::PDB::open(file).map_err(|e| format!("Not a readable .pdb file: {}", e))?;
    let table = pdb.global_symbols().map_err(|e| e.to_string())?;
    let map = pdb.address_map().map_err(|e| e.to_string())?;
    let mut symbols = Vec::new();
    let mut iter = table.iter();
    while let Some(symbol) = iter.next().map_err(|e| e.to_string())? {
        if symbols.len() >= MAX_SYMBOLS {
            break;
        }
        match symbol.parse() {
            Ok(pdb::SymbolData::Public(data)) if data.function => {
                if let Some(rva) = data.offset.to_rva(&map) {
                    symbols.push((rva.0, data.name.to_string().into_owned()));
                }
            }
            Ok(pdb::SymbolData::Procedure(data)) => {
                if let Some(rva) = data.offset.to_rva(&map) {
                    symbols.push((rva.0, data.name.to_string().into_owned()));
                }
            }
            _ => {}
        }
    }
    symbols.sort();
    symbols.dedup_by(|a, b| a.0 == b.0);
    Ok(symbols)
}
