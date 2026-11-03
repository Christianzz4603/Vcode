// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Debug symbols of a library in the DWARF format: the compilation units, the functions with their addresses and
//! lines in the source, the variables and the table that maps addresses to lines of source files.

use std::borrow::Cow;
use std::fmt::Write;
use std::path::{Path, PathBuf};

use gimli::{AttributeValue, EndianSlice, RunTimeEndian};
use object::{Object, ObjectSection};

type Slice<'a> = EndianSlice<'a, RunTimeEndian>;
type Entry<'a, 'b, 'c> = gimli::DebuggingInformationEntry<'a, 'b, Slice<'c>>;

/// The biggest file that is read.
const MAX_FILE: u64 = 256 * 1024 * 1024;
/// The most lines of a report.
const MAX_LINES: usize = 300_000;

fn err(e: gimli::Error) -> String {
    e.to_string()
}

/// The bytes of the library, or of the separate debug file next to it (`<library>.debug`, `<library>.dbg`) when the
/// library itself has no debug information.
fn read_debug(library: &Path) -> Result<(PathBuf, Vec<u8>), String> {
    let mut candidates = vec![library.to_path_buf()];
    for extension in ["debug", "dbg"] {
        candidates.push(PathBuf::from(format!("{}.{}", library.display(), extension)));
        candidates.push(library.with_extension(extension));
    }
    let mut first: Option<(PathBuf, Vec<u8>)> = None;
    for candidate in candidates {
        let Ok(meta) = std::fs::metadata(&candidate) else { continue };
        if !meta.is_file() {
            continue;
        }
        if meta.len() > MAX_FILE {
            return Err(format!("{} is bigger than {} MB", candidate.display(), MAX_FILE / (1024 * 1024)));
        }
        let data = std::fs::read(&candidate).map_err(|e| format!("Cannot read {}: {}", candidate.display(), e))?;
        let has_info = object::File::parse(&*data).map(|f| f.section_by_name(".debug_info").is_some()).unwrap_or(false);
        if has_info {
            return Ok((candidate, data));
        }
        if first.is_none() {
            first = Some((candidate, data));
        }
    }
    first.ok_or_else(|| format!("Cannot read {}", library.display()))
}

fn direct_name(dwarf: &gimli::Dwarf<Slice<'_>>, unit: &gimli::Unit<Slice<'_>>, entry: &Entry<'_, '_, '_>) -> Option<String> {
    for at in [gimli::DW_AT_linkage_name, gimli::DW_AT_MIPS_linkage_name] {
        if let Some(name) = text(dwarf, unit, entry, at) {
            return Some(crate::library::demangle(&name));
        }
    }
    text(dwarf, unit, entry, gimli::DW_AT_name)
}

fn text(
    dwarf: &gimli::Dwarf<Slice<'_>>,
    unit: &gimli::Unit<Slice<'_>>,
    entry: &Entry<'_, '_, '_>,
    at: gimli::DwAt,
) -> Option<String> {
    let value = entry.attr_value(at).ok()??;
    let text = dwarf.attr_string(unit, value).ok()?;
    Some(text.to_string_lossy().into_owned())
}

/// The name of a function or variable, which may be given by the declaration it belongs to.
fn name_of(dwarf: &gimli::Dwarf<Slice<'_>>, unit: &gimli::Unit<Slice<'_>>, entry: &Entry<'_, '_, '_>) -> Option<String> {
    if let Some(name) = direct_name(dwarf, unit, entry) {
        return Some(name);
    }
    for at in [gimli::DW_AT_specification, gimli::DW_AT_abstract_origin] {
        if let Ok(Some(AttributeValue::UnitRef(offset))) = entry.attr_value(at) {
            if let Ok(target) = unit.entry(offset) {
                if let Some(name) = direct_name(dwarf, unit, &target) {
                    return Some(name);
                }
            }
        }
    }
    None
}

fn line_of(entry: &Entry<'_, '_, '_>) -> Option<u64> {
    entry.attr_value(gimli::DW_AT_decl_line).ok()?.and_then(|v| v.udata_value())
}

/// The address and size of a function in the code.
fn span(dwarf: &gimli::Dwarf<Slice<'_>>, unit: &gimli::Unit<Slice<'_>>, entry: &Entry<'_, '_, '_>) -> Option<(u64, u64)> {
    let low = entry.attr_value(gimli::DW_AT_low_pc).ok()??;
    let low = dwarf.attr_address(unit, low).ok()??;
    let size = match entry.attr_value(gimli::DW_AT_high_pc).ok()? {
        Some(AttributeValue::Addr(high)) => high.saturating_sub(low),
        Some(other) => other.udata_value().unwrap_or(0),
        None => 0,
    };
    Some((low, size))
}

/// Runs `work` with the DWARF sections of the library at `path`.
fn with_dwarf<T>(
    path: &str,
    work: impl FnOnce(&Path, &object::File<'_>, &gimli::Dwarf<Slice<'_>>) -> Result<T, String>,
) -> Result<T, String> {
    let (file_path, data) = read_debug(Path::new(path))?;
    let file = object::File::parse(&*data).map_err(|e| e.to_string())?;
    if file.section_by_name(".debug_info").is_none() {
        return Err(format!(
            "{} has no DWARF debug information. If the debug symbols are in a separate file, put it next to the library as <name>.debug",
            file_path.display()
        ));
    }
    let endian = if file.is_little_endian() { RunTimeEndian::Little } else { RunTimeEndian::Big };
    let load = |id: gimli::SectionId| -> Result<Cow<'_, [u8]>, String> {
        match file.section_by_name(id.name()) {
            Some(section) => section.uncompressed_data().map_err(|e| e.to_string()),
            None => Ok(Cow::Borrowed(&[][..])),
        }
    };
    let owned = gimli::Dwarf::load(&load)?;
    let borrow: &dyn for<'a> Fn(&'a Cow<'_, [u8]>) -> Slice<'a> = &|section| EndianSlice::new(section.as_ref(), endian);
    #[allow(deprecated)]
    let dwarf = owned.borrow(borrow);
    work(&file_path, &file, &dwarf)
}

/// The compilation units with their functions and variables.
pub fn report(path: &str) -> Result<String, String> {
    with_dwarf(path, |file_path, file, dwarf| {
        let mut out = String::new();
        let _ = writeln!(out, "Debug symbols (DWARF) from {}", file_path.display());
        let mut sections = String::new();
        for section in file.sections() {
            if let Ok(name) = section.name() {
                if name.starts_with(".debug_") {
                    let _ = write!(sections, " {} {},", name, section.size());
                }
            }
        }
        let _ = writeln!(out, "Sections:{}", sections.trim_end_matches(','));
        let (mut units, mut functions, mut variables, mut types) = (0usize, 0usize, 0usize, 0usize);
        let mut body = String::new();
        let mut lines = 0usize;
        let mut iter = dwarf.units();
        'units: while let Some(header) = iter.next().map_err(err)? {
            let unit = dwarf.unit(header).map_err(err)?;
            units += 1;
            let mut entries = unit.entries();
            let mut depth = 0isize;
            while let Some((delta, entry)) = entries.next_dfs().map_err(err)? {
                depth += delta;
                match entry.tag() {
                    gimli::DW_TAG_compile_unit => {
                        let name = text(dwarf, &unit, entry, gimli::DW_AT_name).unwrap_or_else(|| "?".to_string());
                        let producer = text(dwarf, &unit, entry, gimli::DW_AT_producer).unwrap_or_default();
                        let language = match entry.attr_value(gimli::DW_AT_language).ok().flatten() {
                            Some(AttributeValue::Language(l)) => l.to_string(),
                            _ => String::new(),
                        };
                        let _ = writeln!(body, "\n# {}  [{}; {}]", name, producer, language);
                    }
                    gimli::DW_TAG_subprogram => {
                        if let (Some(name), Some((address, size))) = (name_of(dwarf, &unit, entry), span(dwarf, &unit, entry)) {
                            functions += 1;
                            let line = line_of(entry).map(|l| format!("  line {}", l)).unwrap_or_default();
                            let _ = writeln!(body, "{:x} {:>6}  {}{}", address, size, name, line);
                            lines += 1;
                        }
                    }
                    gimli::DW_TAG_variable if depth <= 1 => {
                        if let Some(name) = name_of(dwarf, &unit, entry) {
                            variables += 1;
                            let line = line_of(entry).map(|l| format!("  line {}", l)).unwrap_or_default();
                            let _ = writeln!(body, "variable {}{}", name, line);
                            lines += 1;
                        }
                    }
                    gimli::DW_TAG_structure_type
                    | gimli::DW_TAG_class_type
                    | gimli::DW_TAG_union_type
                    | gimli::DW_TAG_enumeration_type
                    | gimli::DW_TAG_typedef => types += 1,
                    _ => {}
                }
                if lines >= MAX_LINES {
                    let _ = writeln!(body, "\nThe report stops here, the debug information is bigger");
                    break 'units;
                }
            }
        }
        let _ = writeln!(out, "Units: {}, functions: {}, global variables: {}, types: {}", units, functions, variables, types);
        out.push_str(&body);
        Ok(out)
    })
}

fn file_name(dwarf: &gimli::Dwarf<Slice<'_>>, unit: &gimli::Unit<Slice<'_>>, file: &gimli::FileEntry<Slice<'_>>) -> String {
    match dwarf.attr_string(unit, file.path_name()) {
        Ok(name) => {
            let name = name.to_string_lossy();
            name.rsplit('/').next().unwrap_or(&name).to_string()
        }
        Err(_) => "?".to_string(),
    }
}

/// The table that maps addresses of the code to lines of the source files.
pub fn lines(path: &str) -> Result<String, String> {
    with_dwarf(path, |file_path, _file, dwarf| {
        let mut out = String::new();
        let _ = writeln!(out, "Lines of source files from {}", file_path.display());
        let _ = writeln!(out, "address  file:line");
        let mut count = 0usize;
        let mut iter = dwarf.units();
        'units: while let Some(header) = iter.next().map_err(err)? {
            let unit = dwarf.unit(header).map_err(err)?;
            let Some(program) = unit.line_program.clone() else { continue };
            let mut rows = program.rows();
            let mut last = (0u64, 0u64, String::new());
            while let Some((line_header, row)) = rows.next_row().map_err(err)? {
                if row.end_sequence() || !row.is_stmt() {
                    continue;
                }
                let line = row.line().map(|l| l.get()).unwrap_or(0);
                let name = row.file(line_header).map(|f| file_name(dwarf, &unit, f)).unwrap_or_else(|| "?".to_string());
                if last.1 == line && last.2 == name {
                    continue;
                }
                let _ = writeln!(out, "{:x}  {}:{}", row.address(), name, line);
                last = (row.address(), line, name);
                count += 1;
                if count >= MAX_LINES {
                    let _ = writeln!(out, "\nThe table stops here, it is bigger");
                    break 'units;
                }
            }
        }
        Ok(out)
    })
}
