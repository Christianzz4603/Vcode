// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! ARM64 (A64) decoding on top of yaxpeax-arm.

use yaxpeax_arch::{Decoder, U8Reader};
use yaxpeax_arm::armv8::a64::{InstDecoder, Instruction, Opcode};

pub fn decoder() -> InstDecoder {
    InstDecoder::default()
}

/// Decodes one instruction word at `pc`. Returns the text and the target address of a branch.
/// Offsets that are relative to the program counter are shown as absolute addresses.
pub fn decode(decoder: &InstDecoder, word: u32, pc: u64) -> Option<(String, Option<u64>)> {
    let bytes = word.to_le_bytes();
    let mut inst = Instruction::default();
    let mut reader = U8Reader::new(&bytes);
    decoder.decode_into(&mut inst, &mut reader).ok()?;
    let text = inst.to_string();
    let is_adrp = inst.opcode == Opcode::ADRP;
    Some(resolve_offsets(&text, is_adrp, pc))
}

fn is_branch(mnemonic: &str) -> bool {
    mnemonic == "b"
        || mnemonic == "bl"
        || mnemonic.starts_with("b.")
        || mnemonic == "cbz"
        || mnemonic == "cbnz"
        || mnemonic == "tbz"
        || mnemonic == "tbnz"
}

/// Replaces `$+0x10` and `$-0x10` with the address they point to.
fn resolve_offsets(text: &str, is_adrp: bool, pc: u64) -> (String, Option<u64>) {
    let Some(pos) = text.find('$') else {
        return (text.to_string(), None);
    };
    let rest = &text[pos + 1..];
    let (negative, rest) = match rest.chars().next() {
        Some('+') => (false, &rest[1..]),
        Some('-') => (true, &rest[1..]),
        _ => return (text.to_string(), None),
    };
    let Some(hex) = rest.strip_prefix("0x") else {
        return (text.to_string(), None);
    };
    let digits = hex.find(|c: char| !c.is_ascii_hexdigit()).unwrap_or(hex.len());
    let Ok(value) = u64::from_str_radix(&hex[..digits], 16) else {
        return (text.to_string(), None);
    };
    let offset = if negative { (value as i64).wrapping_neg() } else { value as i64 };
    let base = if is_adrp { pc & !0xfff } else { pc };
    let target = (base as i64).wrapping_add(offset) as u64;
    let mut out = String::with_capacity(text.len() + 8);
    out.push_str(&text[..pos]);
    out.push_str(&format!("0x{:x}", target));
    out.push_str(&hex[digits..]);
    let mnemonic = text.split(' ').next().unwrap_or("");
    let branch_target = if is_branch(mnemonic) { Some(target) } else { None };
    (out, branch_target)
}

/// The address of the GOT entry that a PLT stub (`adrp x16`, `ldr x17, [x16, #imm]`) jumps through.
pub fn plt_got_address(first: u32, second: u32, pc: u64) -> Option<u64> {
    let adrp_x16 = (first & 0x9f00_001f) == 0x9000_0010;
    let ldr_x17 = (second & 0xffc0_0000) == 0xf940_0000 && ((second >> 5) & 0x1f) == 16 && (second & 0x1f) == 17;
    if !adrp_x16 || !ldr_x17 {
        return None;
    }
    let immlo = ((first >> 29) & 3) as i64;
    let immhi = ((first >> 5) & 0x7ffff) as i64;
    let imm = ((immhi << 2) | immlo) << 43 >> 43;
    let page = ((pc & !0xfff) as i64).wrapping_add(imm << 12) as u64;
    let offset = (((second >> 10) & 0xfff) as u64) * 8;
    Some(page.wrapping_add(offset))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn text(word: u32, pc: u64) -> (String, Option<u64>) {
        decode(&decoder(), word, pc).expect("decodes")
    }

    #[test]
    fn simple_instructions() {
        assert_eq!(text(0xd503201f, 0).0, "nop");
        assert_eq!(text(0xd65f03c0, 0).0, "ret");
        assert!(text(0xa9bf7bfd, 0).0.starts_with("stp x29, x30, [sp"));
    }

    #[test]
    fn branch_targets_are_absolute() {
        let (t, target) = text(0x9400_0004, 0x1000);
        assert_eq!(target, Some(0x1010));
        assert!(t.contains("0x1010"), "{}", t);
        let (t, target) = text(0x17ff_fffe, 0x1000);
        assert_eq!(target, Some(0xff8));
        assert!(t.contains("0xff8"), "{}", t);
    }

    #[test]
    fn adrp_uses_the_page() {
        let (t, target) = text(0xb000_0000, 0x1234);
        assert!(t.contains("0x2000"), "{}", t);
        assert_eq!(target, None);
    }

    #[test]
    fn plt_stub_is_found() {
        // adrp x16, #0x1000 ; ldr x17, [x16, #0x18] at 0x1000 reads the GOT entry at 0x2018
        assert_eq!(plt_got_address(0xb000_0010, 0xf940_0e11, 0x1000), Some(0x2018));
        assert_eq!(plt_got_address(0xd503_201f, 0xf940_0e11, 0x1000), None);
    }
}
