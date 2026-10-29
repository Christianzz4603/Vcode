// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Functions that the Kotlin class com.vcode.studio.core.disasm.NativeDisassembler calls.

use std::panic::{catch_unwind, AssertUnwindSafe};

use jni::objects::{JClass, JString};
use jni::sys::{jlong, jstring};
use jni::JNIEnv;

use crate::library::{demangle, Library};

fn read_path(env: &mut JNIEnv, path: &JString) -> Option<String> {
    env.get_string(path).ok().map(|s| s.into())
}

fn run<F>(env: &mut JNIEnv, work: F) -> jstring
where
    F: FnOnce() -> Result<String, String>,
{
    let text = match catch_unwind(AssertUnwindSafe(work)) {
        Ok(Ok(text)) => text,
        Ok(Err(message)) => {
            let _ = env.throw_new("java/lang/IllegalStateException", message);
            return std::ptr::null_mut();
        }
        Err(_) => {
            let _ = env.throw_new("java/lang/IllegalStateException", "The disassembler stopped unexpectedly");
            return std::ptr::null_mut();
        }
    };
    match env.new_string(text) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_vcode_studio_core_disasm_NativeDisassembler_info<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jstring {
    let Some(path) = read_path(&mut env, &path) else { return std::ptr::null_mut() };
    run(&mut env, || Library::open(&path)?.info())
}

#[no_mangle]
pub extern "system" fn Java_com_vcode_studio_core_disasm_NativeDisassembler_symbols<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jstring {
    let Some(path) = read_path(&mut env, &path) else { return std::ptr::null_mut() };
    run(&mut env, || Library::open(&path)?.symbols_text())
}

#[no_mangle]
pub extern "system" fn Java_com_vcode_studio_core_disasm_NativeDisassembler_strings<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jstring {
    let Some(path) = read_path(&mut env, &path) else { return std::ptr::null_mut() };
    run(&mut env, || Library::open(&path)?.strings_text())
}

/// Lines of `address<TAB>size<TAB>name<TAB>readable name`.
#[no_mangle]
pub extern "system" fn Java_com_vcode_studio_core_disasm_NativeDisassembler_functions<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jstring {
    let Some(path) = read_path(&mut env, &path) else { return std::ptr::null_mut() };
    run(&mut env, || {
        let mut out = String::new();
        for f in Library::open(&path)?.functions()? {
            out.push_str(&format!("{:x}\t{:x}\t{}\t{}\n", f.addr, f.size, f.name, demangle(&f.name)));
        }
        Ok(out)
    })
}

/// Lines of `start<TAB>end<TAB>section`.
#[no_mangle]
pub extern "system" fn Java_com_vcode_studio_core_disasm_NativeDisassembler_chunks<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
) -> jstring {
    let Some(path) = read_path(&mut env, &path) else { return std::ptr::null_mut() };
    run(&mut env, || {
        let mut out = String::new();
        for (start, end, name) in Library::open(&path)?.chunks()? {
            out.push_str(&format!("{:x}\t{:x}\t{}\n", start, end, name));
        }
        Ok(out)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_vcode_studio_core_disasm_NativeDisassembler_disassemble<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    path: JString<'local>,
    start: jlong,
    end: jlong,
) -> jstring {
    let Some(path) = read_path(&mut env, &path) else { return std::ptr::null_mut() };
    run(&mut env, || Library::open(&path)?.disassemble(start as u64, end as u64))
}
