// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Makes the text of the Ghidra decompiler easier to read: C type names, plain calls through the GOT,
//! comments with the strings that constants point to, and one line for the unreachable block warnings.

use std::collections::HashMap;

/// The strings of a library by address.
pub type Strings = HashMap<u64, String>;

/// Constants below this are numbers, not addresses.
const MIN_ADDRESS: u64 = 0x1000;
/// The longest string that is shown in a comment.
const MAX_COMMENT: usize = 48;

/// Reads the lines of strings.txt (address, section, text) into a table.
pub fn string_table(text: &str) -> Strings {
    let mut table = Strings::new();
    for line in text.lines() {
        let Some((address, rest)) = line.split_once("  ") else { continue };
        let Ok(address) = u64::from_str_radix(address.trim(), 16) else { continue };
        let Some((_section, string)) = rest.trim_start().split_once(' ') else { continue };
        let string = string.trim();
        if !string.is_empty() {
            table.insert(address, string.to_string());
        }
    }
    table
}

fn c_type(word: &str) -> Option<&'static str> {
    Some(match word {
        "int1" => "int8_t",
        "int2" => "int16_t",
        "int4" => "int32_t",
        "int8" => "int64_t",
        "uint1" | "xunknown1" | "undefined1" => "uint8_t",
        "uint2" | "xunknown2" | "undefined2" => "uint16_t",
        "uint4" | "xunknown4" | "undefined4" => "uint32_t",
        "uint8" | "xunknown8" | "undefined8" => "uint64_t",
        "float4" => "float",
        "float8" => "double",
        _ => return None,
    })
}

fn comment_text(text: &str) -> String {
    let mut out = String::new();
    for (count, c) in text.chars().enumerate() {
        if count >= MAX_COMMENT {
            out.push_str("...");
            break;
        }
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            c if c.is_control() => out.push(' '),
            c => out.push(c),
        }
    }
    out.replace("*/", "* /")
}

fn is_word_start(c: char) -> bool {
    c.is_ascii_alphabetic() || c == '_'
}

fn is_word_part(c: char) -> bool {
    c.is_ascii_alphanumeric() || matches!(c, '_' | '.' | '$' | '@')
}

/// Renames the Ghidra types to C types, turns `(*name@got)(...)` into `name(...)` and adds the text of
/// the strings after the constants that point to them. Comments and quoted text are left as they are.
pub fn tidy(text: &str, strings: &Strings) -> String {
    let src: Vec<char> = text.chars().collect();
    let len = src.len();
    let mut out = String::with_capacity(text.len() + text.len() / 8);
    let mut i = 0;
    while i < len {
        let c = src[i];
        let next = if i + 1 < len { src[i + 1] } else { '\0' };
        if c == '/' && next == '*' {
            let start = i;
            i += 2;
            while i < len && !(src[i] == '*' && i + 1 < len && src[i + 1] == '/') {
                i += 1;
            }
            i = (i + 2).min(len);
            out.extend(src[start..i].iter());
        } else if c == '/' && next == '/' {
            let start = i;
            while i < len && src[i] != '\n' {
                i += 1;
            }
            out.extend(src[start..i].iter());
        } else if c == '"' || c == '\'' {
            let start = i;
            i += 1;
            while i < len && src[i] != c && src[i] != '\n' {
                if src[i] == '\\' {
                    i += 1;
                }
                i += 1;
            }
            if i < len && src[i] == c {
                i += 1;
            }
            out.extend(src[start..i.min(len)].iter());
        } else if is_word_start(c) {
            let start = i;
            while i < len && is_word_part(src[i]) {
                i += 1;
            }
            let word: String = src[start..i].iter().collect();
            if let Some(base) = word.strip_suffix("@got") {
                if out.ends_with("(*") && i + 1 < len && src[i] == ')' && src[i + 1] == '(' {
                    out.truncate(out.len() - 2);
                    out.push_str(base);
                    i += 1;
                } else {
                    out.push_str(base);
                }
            } else if let Some(replacement) = c_type(&word) {
                out.push_str(replacement);
            } else {
                out.push_str(&word);
            }
        } else if c.is_ascii_digit() {
            let start = i;
            while i < len && (src[i].is_ascii_alphanumeric() || src[i] == '_') {
                i += 1;
            }
            let token: String = src[start..i].iter().collect();
            out.push_str(&token);
            if let Some(hex) = token.strip_prefix("0x").or_else(|| token.strip_prefix("0X")) {
                let digits = hex.trim_end_matches(|d: char| matches!(d, 'u' | 'U' | 'l' | 'L'));
                if let Ok(value) = u64::from_str_radix(digits, 16) {
                    if value >= MIN_ADDRESS {
                        if let Some(string) = strings.get(&value) {
                            out.push_str(" /* \"");
                            out.push_str(&comment_text(string));
                            out.push_str("\" */");
                        }
                    }
                }
            }
        } else {
            out.push(c);
            i += 1;
        }
    }
    out
}

/// Removes trailing spaces and double blank lines, puts the opening brace under the signature and
/// replaces the "Removing unreachable block" warnings by one line.
pub fn finish(text: &str) -> String {
    let mut removed = 0usize;
    let mut lines: Vec<&str> = Vec::new();
    for line in text.lines() {
        let line = line.trim_end();
        if line.trim_start().starts_with("/* WARNING: Removing unreachable block") {
            removed += 1;
            continue;
        }
        if line.is_empty() {
            if lines.last().map_or(true, |last| last.is_empty()) {
                continue;
            }
        } else if line == "{" && lines.last().map_or(false, |last| last.is_empty()) {
            lines.pop();
        }
        lines.push(line);
    }
    while lines.last().map_or(false, |last| last.is_empty()) {
        lines.pop();
    }
    let mut out = String::new();
    if removed > 0 {
        let what = if removed == 1 { "block was" } else { "blocks were" };
        out.push_str(&format!("// {} unreachable {} removed by the decompiler\n\n", removed, what));
    }
    for line in lines {
        out.push_str(line);
        out.push('\n');
    }
    out
}

/// The text of the decompiler, made easier to read.
pub fn improve(text: &str, strings: &Strings) -> String {
    finish(&tidy(text, strings))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn renames_types() {
        let none = Strings::new();
        assert_eq!(
            tidy("xunknown8 cs_strerror(uint4 param_1)", &none),
            "uint64_t cs_strerror(uint32_t param_1)"
        );
        assert_eq!(tidy("int4 iVar1; float4 f; uint8 *p;", &none), "int32_t iVar1; float f; uint64_t *p;");
    }

    #[test]
    fn leaves_quoted_text_and_comments() {
        let none = Strings::new();
        let text = "x = \"uint4 xunknown8\"; // uint4\n/* int4 */ c = '\\0';";
        assert_eq!(tidy(text, &none), text);
    }

    #[test]
    fn plain_calls_through_the_got() {
        let none = Strings::new();
        assert_eq!(
            tidy("puVar2 = (uint4 *)(*calloc@got)(1,0xb8);", &none),
            "puVar2 = (uint32_t *)calloc(1,0xb8);"
        );
        assert_eq!(tidy("(*free@got)(puVar2);", &none), "free(puVar2);");
        assert_eq!(
            tidy("if ((malloc@got == 0) || (free@got == (code *)0x0))", &none),
            "if ((malloc == 0) || (free == (code *)0x0))"
        );
    }

    #[test]
    fn comments_after_string_addresses() {
        let mut table = Strings::new();
        table.insert(0x2d2a70, "Invalid argument".to_string());
        assert_eq!(
            tidy("return 0x2d2a70;", &table),
            "return 0x2d2a70 /* \"Invalid argument\" */;"
        );
        let same = "a = 0x10 + 0x2d2a71;";
        assert_eq!(tidy(same, &table), same);
    }

    #[test]
    fn long_strings_are_cut() {
        let mut table = Strings::new();
        table.insert(0x5000, "x".repeat(100));
        let out = tidy("f(0x5000);", &table);
        assert!(out.contains("..."));
        assert!(out.len() < 100);
    }

    #[test]
    fn reads_the_string_table() {
        let text = "001f6740  .rodata    hello world\n... more strings were not listed\nNo strings found\n";
        let table = string_table(text);
        assert_eq!(table.len(), 1);
        assert_eq!(table.get(&0x1f6740).map(|s| s.as_str()), Some("hello world"));
    }

    #[test]
    fn collapses_warnings_and_blank_lines() {
        let text = "\n/* WARNING: Removing unreachable block (ram,0x1) */\n/* WARNING: Removing unreachable block (ram,0x2) */\n\nvoid f(void)\n\n{\n  return;\n}\n\n";
        assert_eq!(
            finish(text),
            "// 2 unreachable blocks were removed by the decompiler\n\nvoid f(void)\n{\n  return;\n}\n"
        );
    }
}
