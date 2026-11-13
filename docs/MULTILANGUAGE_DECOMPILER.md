# Multi-language decompiler integration

The existing Tooliz Decompile tab uses a language-engine registry. New bytecode
engines can be added to the registry without adding another navigation tab.

## Current Java support

- `.jar` archives and standalone `.class` files are handled by CFR 0.152.
- Decompiled Java source is captured through CFR's output-sink API and written
  into the app's cache directory for the existing in-tab source browser.
- Input validation limits archives to 5,000 class entries and individual class
  files to 16 MiB.
- Native `.so` and APK actions keep their existing routes.
- Ordinary `.java`, `.py`, and other source files remain source files and open
  in the editor. They are not bytecode-decompilation targets.

CFR is designed to run on a Java 6 runtime, so it avoids the Procyon dependency
path that caused D8 to reject MethodHandle invocation on the app's API 24
minimum. This compatibility choice still needs confirmation through Android
build and device-runtime testing.

## Engine extension point

Implement `DecompilerEngine` and register the backend in
`DecompilerRegistry`. The existing file manager and Decompile UI should route
supported inputs through the registry.

## Planned backends

Python bytecode (`.pyc`), .NET assemblies, and additional VM bytecode formats
require their own compatible engines. They are not claimed as supported by the
current Java backend.
