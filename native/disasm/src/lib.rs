// Copyright Vcode contributors. Licensed under the Apache License, Version 2.0.

//! Library inspector and ARM64 disassembler. The functions of [`Library`] return text that Vcode shows in the editor.

pub mod arm64;
pub mod decompile;
pub mod library;
mod jni_api;

pub use library::Library;
