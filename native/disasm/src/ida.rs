// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Output in the style of IDA: a listing of the disassembly with a banner, labels, cross references and named calls,
//! and the text of the decompiler with the types, names and layout of Hex-Rays.

use std::collections::{BTreeMap, HashMap};
use std::fmt::Write;

// ------------------------------------------------------------------------------------------------------------------
// The decompiler
// ------------------------------------------------------------------------------------------------------------------

fn is_word_start(c: char) -> bool {
    c.is_ascii_alphabetic() || c == '_'
}

fn is_word_part(c: char) -> bool {
    c.is_ascii_alphanumeric() || c == '_'
}

/// The type of Hex-Rays for a type name of the decompiler of Ghidra or of this engine.
fn ida_type(word: &str) -> Option<&'static str> {
    Some(match word {
        "int1" | "char" => "char",
        "int2" | "short" => "__int16",
        "int4" => "int",
        "int8" | "long" | "longlong" => "__int64",
        "uint1" | "uchar" => "unsigned __int8",
        "uint2" | "ushort" => "unsigned __int16",
        "uint4" | "uint" => "unsigned int",
        "uint8" | "ulong" | "ulonglong" => "unsigned __int64",
        "byte" => "unsigned __int8",
        "xunknown1" | "undefined1" | "undefined" => "char",
        "xunknown2" | "undefined2" => "__int16",
        "xunknown4" | "undefined4" => "int",
        "xunknown8" | "undefined8" => "__int64",
        "float4" => "float",
        "float8" => "double",
        _ => return None,
    })
}

/// The type of an unknown value in the size of the type: the one Hex-Rays writes for pointers and casts.
fn family(kind: &str) -> Option<&'static str> {
    Some(match kind {
        "__int64" | "unsigned __int64" | "_QWORD" => "_QWORD",
        "int" | "unsigned int" | "_DWORD" => "_DWORD",
        "__int16" | "unsigned __int16" | "_WORD" => "_WORD",
        "unsigned __int8" | "_BYTE" => "_BYTE",
        _ => return None,
    })
}

const FAMILY_TYPES: [&str; 11] = [
    "unsigned __int64",
    "__int64",
    "_QWORD",
    "unsigned int",
    "int",
    "_DWORD",
    "unsigned __int16",
    "__int16",
    "_WORD",
    "unsigned __int8",
    "_BYTE",
];

fn hex_name(digits: &str) -> String {
    let trimmed = digits.trim_start_matches('0');
    if trimmed.is_empty() { "0".to_string() } else { trimmed.to_ascii_uppercase() }
}

/// `iVar1`, `puVar2`, `lVar3`: the variables that the decompiler makes up.
fn is_made_up(word: &str) -> bool {
    let Some(at) = word.find("Var") else { return false };
    let (prefix, digits) = (&word[..at], &word[at + 3..]);
    at >= 1 && at <= 5 && prefix.chars().all(|c| c.is_ascii_lowercase()) && !digits.is_empty()
        && digits.chars().all(|c| c.is_ascii_digit())
}

struct Names {
    variables: HashMap<String, String>,
    stack: HashMap<String, String>,
    labels: HashMap<String, String>,
}

impl Names {
    fn variable(&mut self, word: &str) -> String {
        if let Some(name) = self.variables.get(word) {
            return name.clone();
        }
        let name = format!("v{}", self.variables.len() + 1);
        if let Some(offset) = word.strip_prefix("local_") {
            self.stack.insert(name.clone(), format!("[sp-{}h]", hex_name(offset)));
        }
        self.variables.insert(word.to_string(), name.clone());
        name
    }

    fn label(&mut self, word: &str) -> String {
        if let Some(name) = self.labels.get(word) {
            return name.clone();
        }
        let name = format!("LABEL_{}", self.labels.len() + 1);
        self.labels.insert(word.to_string(), name.clone());
        name
    }
}

fn rename_word(word: &str, names: &mut Names) -> String {
    if let Some(kind) = ida_type(word) {
        return kind.to_string();
    }
    if let Some(digits) = word.strip_prefix("FUN_") {
        if !digits.is_empty() && digits.chars().all(|c| c.is_ascii_hexdigit()) {
            return format!("sub_{}", hex_name(digits));
        }
    }
    if let Some(digits) = word.strip_prefix("DAT_") {
        if !digits.is_empty() && digits.chars().all(|c| c.is_ascii_hexdigit()) {
            return format!("unk_{}", hex_name(digits));
        }
    }
    if let Some(digits) = word.strip_prefix("LAB_") {
        if !digits.is_empty() && digits.chars().all(|c| c.is_ascii_hexdigit()) {
            return names.label(word);
        }
    }
    if let Some(number) = word.strip_prefix("param_") {
        if !number.is_empty() && number.chars().all(|c| c.is_ascii_digit()) {
            return format!("a{}", number);
        }
    }
    if let Some(offset) = word.strip_prefix("local_") {
        if !offset.is_empty() && offset.chars().all(|c| c.is_ascii_hexdigit()) {
            return names.variable(word);
        }
    }
    if is_made_up(word) {
        return names.variable(word);
    }
    word.to_string()
}

/// Renames the types, variables, functions and labels. Comments and strings stay as they are.
fn rename_all(text: &str, names: &mut Names) -> String {
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
            out.push_str(&rename_word(&word, names));
        } else if c.is_ascii_digit() {
            let start = i;
            while i < len && (src[i].is_ascii_alphanumeric() || src[i] == '_') {
                i += 1;
            }
            out.extend(src[start..i].iter());
        } else if c == ',' {
            // Hex-Rays puts a space after every comma
            out.push(',');
            if next != ' ' && next != '\n' && next != '\0' {
                out.push(' ');
            }
            i += 1;
        } else {
            out.push(c);
            i += 1;
        }
    }
    out
}

/// `(unsigned int *)` becomes `(_DWORD *)` and so on: pointers to values of a known size, as Hex-Rays writes them.
fn pointer_casts(line: &str) -> String {
    let mut out = line.to_string();
    for kind in FAMILY_TYPES {
        if let Some(short) = family(kind) {
            for stars in ["*", "**", "***"] {
                let from = format!("({} {})", kind, stars);
                if out.contains(&from) {
                    out = out.replace(&from, &format!("({} {})", short, stars));
                }
            }
            let from = format!("({}*)", kind);
            if out.contains(&from) {
                out = out.replace(&from, &format!("({} *)", short));
            }
        }
    }
    out
}

/// The declaration `type *name;` with a type of a known size, in the family type.
fn pointer_declaration(line: &str) -> String {
    let indent_len = line.len() - line.trim_start().len();
    let (indent, body) = line.split_at(indent_len);
    for kind in FAMILY_TYPES {
        if let Some(rest) = body.strip_prefix(kind) {
            if rest.starts_with(" *") && rest.ends_with(';') {
                if let Some(short) = family(kind) {
                    return format!("{}{}{}", indent, short, rest);
                }
            }
        }
    }
    line.to_string()
}

fn split_arguments(args: &str) -> Vec<String> {
    let mut parts = Vec::new();
    let mut depth = 0i32;
    let mut current = String::new();
    for c in args.chars() {
        match c {
            '(' | '[' => {
                depth += 1;
                current.push(c);
            }
            ')' | ']' => {
                depth -= 1;
                current.push(c);
            }
            ',' if depth == 0 => {
                parts.push(current.trim().to_string());
                current.clear();
            }
            _ => current.push(c),
        }
    }
    if !current.trim().is_empty() {
        parts.push(current.trim().to_string());
    }
    parts
}

/// `type name(arguments)` as Hex-Rays writes the signature: `type __fastcall name(arguments)`.
fn signature(line: &str, convention: Option<&str>) -> String {
    let Some(open) = line.find('(') else { return line.to_string() };
    let Some(close) = line.rfind(')') else { return line.to_string() };
    if close < open {
        return line.to_string();
    }
    let head = line[..open].trim_end();
    let name_start = head.rfind(|c: char| !is_word_part(c)).map(|p| p + 1).unwrap_or(0);
    let name = &head[name_start..];
    let kind = head[..name_start].trim_end();
    if name.is_empty() {
        return line.to_string();
    }
    let mut arguments = split_arguments(&line[open + 1..close]);
    if arguments.len() == 1 && arguments[0] == "void" {
        arguments.clear();
    }
    let arguments = if arguments.is_empty() { "void".to_string() } else { arguments.join(", ") };
    let mut out = String::new();
    out.push_str(kind);
    if let Some(convention) = convention {
        if !kind.is_empty() && !kind.ends_with('*') {
            out.push(' ');
        }
        out.push_str(convention);
        out.push(' ');
    } else if !kind.is_empty() {
        out.push(if kind.ends_with('*') { '\0' } else { ' ' });
        if out.ends_with('\0') {
            out.pop();
        }
    }
    out.push_str(name);
    out.push('(');
    out.push_str(&arguments);
    out.push(')');
    out.push_str(line[close + 1..].trim_end());
    out
}

fn is_declaration(trimmed: &str) -> Option<&str> {
    // `type name;` or `type *name;` or `type name[4];`
    if !trimmed.ends_with(';') || trimmed.contains('=') || trimmed.contains('(') {
        return None;
    }
    let body = trimmed.trim_end_matches(';');
    let first_word = body.split_whitespace().next().unwrap_or("");
    if matches!(first_word, "return" | "goto" | "else" | "case" | "break" | "continue" | "do") {
        return None;
    }
    let name_start = body.rfind(|c: char| !is_word_part(c)).map(|p| p + 1).unwrap_or(0);
    let name = &body[name_start..];
    if name.is_empty() || name_start == 0 {
        return None;
    }
    Some(name)
}

/// The name IDA gives to a function that has none: `sub_` and the address in upper case.
pub fn function_name(name: &str) -> String {
    if let Some(digits) = name.strip_prefix("FUN_") {
        if !digits.is_empty() && digits.chars().all(|c| c.is_ascii_hexdigit()) {
            return format!("sub_{}", hex_name(digits));
        }
    }
    name.to_string()
}

const KEYWORDS: [&str; 5] = ["if (", "while (", "for (", "switch (", "else if ("];

/// The layout of Hex-Rays: braces under the statement, spaces inside the parentheses, `else` on its own line.
fn layout(line: &str, out: &mut Vec<String>) {
    let indent_len = line.len() - line.trim_start().len();
    let (indent, body) = line.split_at(indent_len);
    let mut rest = body;
    if let Some(after) = rest.strip_prefix("} ") {
        if !after.is_empty() {
            out.push(format!("{}}}", indent));
            rest = after;
        }
    }
    if rest == "else {" {
        out.push(format!("{}else", indent));
        out.push(format!("{}{{", indent));
        return;
    }
    if rest == "do {" {
        out.push(format!("{}do", indent));
        out.push(format!("{}{{", indent));
        return;
    }
    if let Some(condition) = rest.strip_prefix("while (").and_then(|r| r.strip_suffix(");")) {
        if body.starts_with("} ") {
            out.push(format!("{}while ( {} );", indent, condition));
            return;
        }
    }
    if rest.ends_with(") {") {
        for keyword in KEYWORDS {
            if let Some(inner) = rest.strip_prefix(keyword).and_then(|r| r.strip_suffix(") {")) {
                out.push(format!("{}{} ( {} )", indent, keyword.trim_end_matches(" ("), inner));
                out.push(format!("{}{{", indent));
                return;
            }
        }
    }
    // `if (condition) statement;` becomes two lines
    if let Some(after_if) = rest.strip_prefix("if (") {
        let mut depth = 1i32;
        let mut close = None;
        for (at, c) in after_if.char_indices() {
            match c {
                '(' => depth += 1,
                ')' => {
                    depth -= 1;
                    if depth == 0 {
                        close = Some(at);
                        break;
                    }
                }
                _ => {}
            }
        }
        if let Some(at) = close {
            let statement = after_if[at + 1..].trim();
            if !statement.is_empty() && statement != "{" && !statement.starts_with("/*") && !statement.starts_with("//") {
                out.push(format!("{}if ( {} )", indent, &after_if[..at]));
                out.push(format!("{}  {}", indent, statement));
                return;
            }
        }
    }
    if rest.len() != body.len() {
        out.push(format!("{}{}", indent, rest));
    } else {
        out.push(line.to_string());
    }
}

/// The text of the decompiler as Hex-Rays writes it: `__int64 __fastcall sub_1234(__int64 a1)`, `a1` for the
/// arguments, `v1` for the variables, `if ( … )` with the braces under it, and `//` comments.
///
/// `convention` is written after the type of the function, for example `__fastcall`.
pub fn hexrays(text: &str, convention: Option<&str>) -> String {
    let mut names = Names { variables: HashMap::new(), stack: HashMap::new(), labels: HashMap::new() };
    let renamed = rename_all(text, &mut names);
    let lines: Vec<&str> = renamed.lines().collect();
    // The signature is the line above the first brace on its own
    let brace = lines.iter().position(|l| l.trim_end() == "{");
    let signature_at = brace.and_then(|b| (0..b).rev().find(|&i| !lines[i].trim().is_empty()));
    let mut out: Vec<String> = Vec::with_capacity(lines.len() + lines.len() / 4);
    for (index, raw) in lines.iter().enumerate() {
        let line = raw.trim_end();
        if Some(index) == signature_at && line.contains('(') && !line.starts_with(char::is_whitespace) {
            out.push(pointer_casts(&signature(line, convention)));
            continue;
        }
        let trimmed = line.trim_start();
        // A comment on a line of its own
        if trimmed.starts_with("/*") && trimmed.ends_with("*/") && trimmed.matches("*/").count() == 1 {
            let indent = &line[..line.len() - trimmed.len()];
            let inner = trimmed[2..trimmed.len() - 2].trim();
            out.push(format!("{}// {}", indent, inner));
            continue;
        }
        // A label stands at the left edge
        if trimmed.starts_with("LABEL_") && trimmed.ends_with(':') {
            out.push(trimmed.to_string());
            continue;
        }
        let converted = pointer_declaration(&pointer_casts(line));
        if let Some(name) = is_declaration(converted.trim_start()) {
            if let Some(place) = names.stack.get(name) {
                out.push(format!("{} // {}", converted, place));
                continue;
            }
        }
        layout(&converted, &mut out);
    }
    // Hex-Rays has no empty line between the signature and the brace
    let mut cleaned: Vec<String> = Vec::with_capacity(out.len());
    for line in out {
        if line == "{" && cleaned.last().map_or(false, |last| last.is_empty()) {
            cleaned.pop();
        }
        cleaned.push(line);
    }
    let out = cleaned;
    let mut result = out.join("\n");
    if text.ends_with('\n') {
        result.push('\n');
    }
    result
}

// ------------------------------------------------------------------------------------------------------------------
// The listing
// ------------------------------------------------------------------------------------------------------------------

/// One instruction of the disassembly.
pub struct Line {
    pub address: u64,
    pub mnemonic: String,
    pub operands: String,
}

/// Who refers to the function from outside of it: the name of the function, the offset in it and `p` for a call.
pub struct Caller {
    pub name: String,
    pub offset: u64,
    pub address: u64,
    pub kind: char,
}

pub struct Listing<'a> {
    pub name: &'a str,
    pub section: &'a str,
    /// Whether the addresses have 16 digits, and not 8.
    pub wide: bool,
    /// ARM style: upper case registers and mnemonics, `#0x10` for numbers.
    pub arm: bool,
    pub public: bool,
    pub lines: &'a [Line],
    /// The names of the functions by their address.
    pub names: &'a BTreeMap<u64, String>,
    pub callers: &'a [Caller],
    /// Other comment lines about the function, for example the strings it uses.
    pub notes: &'a [String],
}

fn is_branch(mnemonic: &str, arm: bool) -> Option<char> {
    let m = mnemonic.to_ascii_lowercase();
    if arm {
        match m.as_str() {
            "bl" | "blx" => Some('p'),
            "b" | "cbz" | "cbnz" | "tbz" | "tbnz" => Some('j'),
            _ if m.starts_with("b.") => Some('j'),
            _ => None,
        }
    } else if m == "call" {
        Some('p')
    } else if m.starts_with('j') || m.starts_with("loop") {
        Some('j')
    } else {
        None
    }
}

/// The address at the end of the operands, such as `0x00101234` or `0x101234`, and where it starts in the text.
fn target_of(operands: &str) -> Option<(u64, usize)> {
    let at = operands.rfind("0x").or_else(|| operands.rfind("0X"))?;
    let digits: String = operands[at + 2..].chars().take_while(|c| c.is_ascii_hexdigit()).collect();
    if digits.is_empty() || at + 2 + digits.len() != operands.trim_end().len() {
        return None;
    }
    Some((u64::from_str_radix(&digits, 16).ok()?, at))
}

/// A number the way IDA writes it in x86 code: `10h`, `0FFh`, and decimal for small numbers.
fn ida_number(value: u64) -> String {
    if value < 10 {
        return value.to_string();
    }
    let hex = format!("{:X}", value);
    if hex.starts_with(|c: char| c.is_ascii_alphabetic()) { format!("0{}h", hex) } else { format!("{}h", hex) }
}

/// Operands in the form of IDA: lower case and `10h` for x86, upper case registers and `#0x10` for ARM.
fn style_operands(operands: &str, arm: bool) -> String {
    let mut out = String::with_capacity(operands.len() + 4);
    let chars: Vec<char> = operands.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        let c = chars[i];
        if c == '0' && i + 1 < chars.len() && (chars[i + 1] == 'x' || chars[i + 1] == 'X') {
            let mut j = i + 2;
            while j < chars.len() && chars[j].is_ascii_hexdigit() {
                j += 1;
            }
            let digits: String = chars[i + 2..j].iter().collect();
            if let Ok(value) = u64::from_str_radix(&digits, 16) {
                if arm {
                    out.push_str(&format!("0x{:X}", value));
                } else {
                    out.push_str(&ida_number(value));
                }
                i = j;
                continue;
            }
        }
        if c == ',' {
            out.push(',');
            if i + 1 < chars.len() && chars[i + 1] != ' ' {
                out.push(' ');
            }
            i += 1;
            continue;
        }
        out.push(if arm { c.to_ascii_uppercase() } else { c.to_ascii_lowercase() });
        i += 1;
    }
    if arm {
        out
    } else {
        // [rbp + -4] is written [rbp-4], [rax + rcx*4] is written [rax+rcx*4]
        out.replace(" + -", "-").replace(" + ", "+").replace(" - ", "-")
    }
}

fn arrow(from: u64, to: u64) -> char {
    if from < to { '↑' } else { '↓' }
}

fn offset_text(name: &str, offset: u64) -> String {
    if offset == 0 { name.to_string() } else { format!("{}+{:X}", name, offset) }
}

/// A listing of one function in the style of IDA.
pub fn listing(input: &Listing) -> String {
    let width = if input.wide { 16 } else { 8 };
    let first = input.lines.first().map(|l| l.address).unwrap_or(0);
    let last = input.lines.last().map(|l| l.address).unwrap_or(first);
    let end = input.lines.last().map(|l| l.address + 1).unwrap_or(first);
    let prefix = |address: u64| format!("{}:{:0width$X}", input.section, address, width = width);
    let mnemonic_width = if input.arm { 16 } else { 8 };
    // Places in the function that are jumped to
    let mut targets: BTreeMap<u64, Vec<(u64, char)>> = BTreeMap::new();
    for line in input.lines {
        if let Some(kind) = is_branch(&line.mnemonic, input.arm) {
            if let Some((target, _)) = target_of(&line.operands) {
                if kind == 'j' && target >= first && target < end {
                    targets.entry(target).or_default().push((line.address, kind));
                }
            }
        }
    }
    let comment_column = 48usize;
    let mut out = String::new();
    let mut put = |out: &mut String, address: u64, text: &str| {
        let _ = writeln!(out, "{}", format!("{} {}", prefix(address), text).trim_end());
    };
    put(&mut out, first, &format!("; {}", "=============== S U B R O U T I N E ======================================="));
    put(&mut out, first, "");
    for note in input.notes {
        put(&mut out, first, &format!("; {}", note));
    }
    if !input.notes.is_empty() {
        put(&mut out, first, "");
    }
    if input.public {
        put(&mut out, first, &format!("{:16}public {}", "", input.name));
    }
    let mut head = format!("{:<16}proc near", input.name);
    if let Some(caller) = input.callers.first() {
        head = format!(
            "{:<w$}; {} XREF: {}{}{}",
            head,
            "CODE",
            offset_text(&caller.name, caller.offset),
            arrow(caller.address, first),
            caller.kind,
            w = comment_column
        );
    }
    put(&mut out, first, &head);
    for caller in input.callers.iter().skip(1) {
        put(
            &mut out,
            first,
            &format!("{:<w$}; {}{}{}", "", offset_text(&caller.name, caller.offset), arrow(caller.address, first), caller.kind, w = comment_column),
        );
    }
    put(&mut out, first, "");
    for line in input.lines {
        if line.address != first {
            if let Some(sources) = targets.get(&line.address) {
                let label = format!("loc_{:X}:", line.address);
                let mut places = sources.iter();
                if let Some((from, kind)) = places.next() {
                    put(
                        &mut out,
                        line.address,
                        &format!(
                            "{:<w$}; CODE XREF: {}{}{}",
                            label,
                            offset_text(input.name, from - first),
                            arrow(*from, line.address),
                            kind,
                            w = comment_column
                        ),
                    );
                }
                for (from, kind) in places {
                    put(
                        &mut out,
                        line.address,
                        &format!("{:<w$}; {}{}{}", "", offset_text(input.name, from - first), arrow(*from, line.address), kind, w = comment_column),
                    );
                }
            }
        }
        let mut operands = style_operands(&line.operands, input.arm);
        let mnemonic = if input.arm { line.mnemonic.to_ascii_uppercase() } else { line.mnemonic.to_ascii_lowercase() };
        if is_branch(&line.mnemonic, input.arm).is_some() {
            if let Some((target, at)) = target_of(&line.operands) {
                let name = input.names.get(&target).cloned().or_else(|| {
                    if target == first {
                        Some(input.name.to_string())
                    } else if target > first && target < end {
                        Some(format!("loc_{:X}", target))
                    } else {
                        None
                    }
                });
                let name = name.unwrap_or_else(|| format!("sub_{:X}", target));
                let mut before = style_operands(&line.operands[..at], input.arm);
                if before.ends_with(',') {
                    before.push(' ');
                }
                operands = format!("{}{}", before, name);
            }
        }
        let padded = if mnemonic.len() >= mnemonic_width { format!("{} ", mnemonic) } else { format!("{:<mw$}", mnemonic, mw = mnemonic_width) };
        let text = format!("{:16}{}{}", "", padded, operands);
        put(&mut out, line.address, text.trim_end());
    }
    put(&mut out, last, &format!("{:<16}endp", input.name));
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE: &str = "\nxunknown8 FUN_00101130(long param_1,int4 param_2)\n\n{\n  long lVar1;\n  xunknown8 local_18;\n  uint4 *puVar2;\n  \n  lVar1 = *(long *)(param_1 + 8);\n  if (lVar1 == 0) {\n    return 0;\n  }\n  puVar2 = (uint4 *)(*calloc@got)(1,0xb8);\n  if ((puVar2 == (uint4 *)0x0) || (param_2 != 1)) {\n    /* WARNING: Subroutine does not return */\n    FUN_00101300(0x2d2a70);\n  }\n  else {\n    local_18 = FUN_00101200(lVar1,param_2);\n  }\n  do {\n    param_2 = param_2 + -1;\n  } while (param_2 != 0);\n  while (lVar1 != 0) {\n    lVar1 = *(long *)(lVar1 + 0x10);\n    if (lVar1 == 5) goto LAB_00101188;\n  }\nLAB_00101188:\n  return local_18;\n}\n\n";

    #[test]
    fn names_of_functions() {
        assert_eq!(function_name("FUN_00101130"), "sub_101130");
        assert_eq!(function_name("main"), "main");
        assert_eq!(function_name("FUN_zz"), "FUN_zz");
    }

    #[test]
    fn signature_of_a_function() {
        let text = "xunknown8 cs_strerror(uint4 param_1)\n\n{\n  return 0;\n}\n";
        let out = hexrays(text, Some("__fastcall"));
        assert!(out.starts_with("__int64 __fastcall cs_strerror(unsigned int a1)\n{\n"), "{}", out);
    }

    #[test]
    fn shows_a_whole_function() {
        let out = hexrays(SAMPLE, Some("__fastcall"));
        println!("{}", out);
        assert!(out.contains("__int64 __fastcall sub_101130(__int64 a1, int a2)\n{\n"));
        assert!(out.contains("  __int64 v1;\n"));
        assert!(out.contains("  __int64 v2; // [sp-18h]\n"));
        assert!(out.contains("  _DWORD *v3;\n"));
        assert!(out.contains("  if ( v1 == 0 )\n  {\n    return 0;\n  }\n"));
        assert!(out.contains("  else\n  {\n"));
        assert!(out.contains("  do\n  {\n"));
        assert!(out.contains("  while ( a2 != 0 );\n"));
        assert!(out.contains("// WARNING: Subroutine does not return"));
        assert!(out.contains("\nLABEL_1:\n"));
        assert!(out.contains("goto LABEL_1;"));
        assert!(out.contains("(_DWORD *)(*calloc@got)(1, 0xb8)"));
        assert!(out.contains("    if ( v1 == 5 )\n      goto LABEL_1;\n"), "{}", out);
    }

    #[test]
    fn strings_and_comments_stay() {
        let text = "void f(void)\n\n{\n  puts(\"param_1 FUN_00101130 uint4\"); // param_2\n}\n";
        let out = hexrays(text, None);
        assert!(out.contains("\"param_1 FUN_00101130 uint4\"") && out.contains("// param_2"), "{}", out);
        assert!(out.starts_with("void f(void)\n{"));
    }

    #[test]
    fn pointer_returns_have_no_space() {
        let out = hexrays("char * get_name(int4 param_1)\n\n{\n  return 0;\n}\n", Some("__fastcall"));
        assert!(out.starts_with("char *__fastcall get_name(int a1)\n"), "{}", out);
    }

    fn lines(items: &[(u64, &str, &str)]) -> Vec<Line> {
        items.iter().map(|(a, m, o)| Line { address: *a, mnemonic: m.to_string(), operands: o.to_string() }).collect()
    }

    #[test]
    fn listing_x86() {
        let code = lines(&[
            (0x1139, "PUSH", "RBP"),
            (0x113a, "MOV", "RBP,RSP"),
            (0x113d, "SUB", "RSP,0x10"),
            (0x1141, "CMP", "dword ptr [RBP + -0x4],0xff"),
            (0x1145, "JZ", "0x1150"),
            (0x1147, "CALL", "0x1200"),
            (0x114c, "JMP", "0x1139"),
            (0x1150, "LEAVE", ""),
            (0x1151, "RET", ""),
        ]);
        let mut names = BTreeMap::new();
        names.insert(0x1200, "puts".to_string());
        let callers = vec![
            Caller { name: "main".into(), offset: 0x1c, address: 0x1050, kind: 'p' },
            Caller { name: "sub_2000".into(), offset: 0, address: 0x2000, kind: 'p' },
        ];
        let out = listing(&Listing {
            name: "sub_1139", section: ".text", wide: true, arm: false, public: false,
            lines: &code, names: &names, callers: &callers, notes: &[],
        });
        println!("{}", out);
        assert!(out.contains(".text:0000000000001139 sub_1139        proc near"));
        assert!(out.contains("CODE XREF: main+1C↑p"));
        assert!(out.contains("sub_2000↓p"));
        assert!(out.contains(".text:0000000000001150 loc_1150:"));
        assert!(out.contains("CODE XREF: sub_1139+C↑j"));
        assert!(out.contains("call    puts"));
        assert!(out.contains("jmp     sub_1139"));
        assert!(out.contains("sub     rsp, 10h"));
        assert!(out.contains("cmp     dword ptr [rbp-4], 0FFh"), "{}", out);
        assert!(out.trim_end().ends_with("endp"));
    }

    #[test]
    fn listing_arm() {
        let code = lines(&[
            (0x38fb0, "stp", "x29,x30,[sp, #-0x10]!"),
            (0x38fb4, "bl", "0x39000"),
            (0x38fb8, "cbz", "x0,0x38fc4"),
            (0x38fbc, "b", "0x38fb0"),
            (0x38fc0, "nop", ""),
            (0x38fc4, "ret", ""),
        ]);
        let mut names = BTreeMap::new();
        names.insert(0x39000, "malloc".to_string());
        let out = listing(&Listing {
            name: "sub_38FB0", section: ".text", wide: true, arm: true, public: true,
            lines: &code, names: &names, callers: &[], notes: &["xrefs to this function (2): main, init".to_string()],
        });
        println!("{}", out);
        assert!(out.contains("STP             X29, X30, [SP, #-0x10]!"), "{}", out);
        assert!(out.contains("BL              malloc"));
        assert!(out.contains("CBZ             X0, loc_38FC4"));
        assert!(out.contains("public sub_38FB0"));
    }
}
