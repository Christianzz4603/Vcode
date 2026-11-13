# Multi-language decompiler integration

The existing Toolix Decompiler tab now has a language-engine registry. The first registered backend is Procyon for Java `.jar` and `.class` inputs.

## Current behavior

- Tapping a `.jar` or `.class` file in Files opens the existing Decompiler tab directly.
- JAR entries are enumerated and decompiled one class at a time with Procyon. Multi-release entries and `module-info.class` are skipped to avoid duplicate/unsupported output.
- Decompiled `.java` files are shown in the in-tab source browser and can be exported to a sibling `*-src` folder.
- Native `.so` and APK actions keep their existing routes.
- `.java`, `.py`, and other source files remain source files and open in the editor; they are not bytecode decompilation targets.

## Engine extension point

Implement `DecompilerEngine`, then register the backend in `DecompilerRegistry`. The UI and file manager use the registry instead of adding another navigation tab.

## Planned backends

Python bytecode (`.pyc`), .NET assemblies, and additional VM bytecode formats need their own compatible engines and are not claimed as supported by this first Java implementation.
