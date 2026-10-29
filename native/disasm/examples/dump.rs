// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Prints what the library functions produce, for checking by hand and in CI.
//! Usage: dump LIBRARY [raw COUNT]

use vcode_disasm::Library;

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let library = Library::open(&args[1]).expect("open");
    if args.len() > 3 && args[2] == "raw" {
        let count: usize = args[3].parse().unwrap();
        let chunks = library.chunks().unwrap();
        let mut done = 0;
        for (start, end, _) in chunks {
            let text = library.disassemble(start, end).unwrap();
            for line in text.lines() {
                if line.len() > 22 && line.as_bytes()[8] == b':' {
                    println!("RAW\t{}\t{}\t{}", &line[..8], &line[11..19], &line[21..]);
                    done += 1;
                    if done >= count {
                        return;
                    }
                }
            }
        }
        return;
    }
    println!("=== info");
    println!("{}", library.info().unwrap());
    let functions = library.functions().unwrap();
    println!("=== functions: {}", functions.len());
    for f in functions.iter().take(8) {
        println!("{:x} {:x} {}", f.addr, f.size, f.name);
    }
    println!("=== symbols lines: {}", library.symbols_text().unwrap().lines().count());
    let strings = library.strings_text().unwrap();
    println!("=== strings lines: {}", strings.lines().count());
    for line in strings.lines().take(5) {
        println!("{}", line);
    }
    let chunks = library.chunks().unwrap();
    println!("=== chunks: {}", chunks.len());
    if let Some(f) = functions.iter().find(|f| f.size >= 48 && !f.name.contains("@plt")) {
        println!("=== disassembly of {}", f.name);
        println!("{}", library.disassemble(f.addr, f.addr + f.size.min(120)).unwrap());
    }
    if let Some(f) = functions.iter().find(|f| f.name.contains("@plt")) {
        println!("=== plt stub {}", f.name);
        println!("{}", library.disassemble(f.addr, f.addr + 16).unwrap());
    }
}
