// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Pseudocode of a function, made by the Rust port of the Ghidra decompiler.

use std::path::Path;
use std::sync::Mutex;
use std::time::SystemTime;

use ghidra_decompiler::Program;

use crate::library::Library;
use crate::pseudo::{self, Strings};

/// The decompiler goes deep into recursion for big functions, so it runs on a thread with a big stack.
const STACK: usize = 64 * 1024 * 1024;

/// Libraries bigger than this get no comments with strings, because reading them a second time takes too long.
const MAX_STRING_SCAN: u64 = 64 * 1024 * 1024;

struct Cached {
    path: String,
    length: u64,
    modified: Option<SystemTime>,
    program: Program,
    strings: Strings,
}

/// The last library that was opened, so that the next function of it is decompiled without reading it again.
static CACHE: Mutex<Option<Cached>> = Mutex::new(None);

fn in_big_stack<T: Send>(work: impl FnOnce() -> T + Send) -> Result<T, String> {
    std::thread::scope(|scope| {
        let handle = std::thread::Builder::new()
            .stack_size(STACK)
            .spawn_scoped(scope, work)
            .map_err(|e| e.to_string())?;
        handle.join().map_err(|_| "The decompiler stopped unexpectedly".to_string())
    })
}

/// The strings of the library, empty when they cannot be read.
fn read_strings(path: &str, length: u64) -> Strings {
    if length > MAX_STRING_SCAN {
        return Strings::new();
    }
    match Library::open(path).and_then(|library| library.strings_text()) {
        Ok(text) => pseudo::string_table(&text),
        Err(_) => Strings::new(),
    }
}

pub fn decompile(path: &str, address: u64) -> Result<String, String> {
    let meta = std::fs::metadata(path).map_err(|e| format!("Cannot read {}: {}", path, e))?;
    let length = meta.len();
    let modified = meta.modified().ok();
    let mut guard = CACHE.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
    let fresh = matches!(&*guard, Some(c) if c.path == path && c.length == length && c.modified == modified);
    if !fresh {
        *guard = None;
        let program = in_big_stack(|| Program::open(Path::new(path), None).map_err(|e| e.to_string()))??;
        let strings = read_strings(path, length);
        *guard = Some(Cached { path: path.to_string(), length, modified, program, strings });
    }
    let cached = guard.as_mut().ok_or_else(|| "No library is open".to_string())?;
    let result = in_big_stack(|| cached.program.decompile(address).map_err(|e| e.to_string()));
    let improved = result.map(|inner| inner.map(|text| pseudo::improve(&text, &cached.strings)));
    match improved {
        Ok(inner) => inner,
        Err(message) => {
            *guard = None;
            Err(message)
        }
    }
}
