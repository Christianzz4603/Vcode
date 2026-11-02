# Changelog

Vcode uses one version per feature or update:

* `versionCode` goes up by 1 for every new feature or update (the base is 10028).
* `versionName` is `1.<n>.0`, where `n` is `versionCode - 10028`.
* A small fix without a new feature can bump only the patch number, for example `1.5.1` with `versionCode` 10034.

## 2.4.0 (10056)

* Cross references and the call graph now come from the Rust Ghidra engine, so the Xrefs and Callgraph reports and the caller and callee comments in the decompiler work for every architecture the engine reads, not only ARM64.
* The engine is pinned to its latest version, which builds for Android again.

## 2.3.1 (10055)

* The Rust Ghidra engine (ghidra-rust-engine) is updated to its latest version: file size guard in every loader, a shared cache budget for the decompiler workers and a check for oversized instructions.

## 2.3.0 (10054)

* Toolix is now the only app: the default build and the release are Toolix (`app.toolix`), the Vcode editor stays inside it as the Vcode tab. The separate Vcode APK (`com.vcode.studio`) is no longer built, `-Pedition=vcode` still builds it locally.

## 2.2.1 (10053)

* Toolix: the settings show the name and the icon of Toolix instead of Vcode (About and the other texts)

## 2.2.0 (10052)

* Plugin API 2: plugins can add actions to the tools menu of files in the file manager (`file.actions`), add buttons to the disassembler and decompiler tabs (`library.tools`) and read native libraries (`library.read`). Errors in a plugin show a message with its id. A .zip or plugin.json can be installed from the tools menu of the file manager. See docs/PLUGINS.md.
* Faster libraries: the native code keeps the last library in memory instead of reading the whole file for every function, the list of functions is built once, and searching functions runs in the background.

## 2.1.0 (10051)

* Software Toolz is now Toolix (`app.toolix`, installs next to the old Software Toolz app)
* Toolix: Settings tab with the same settings as in Vcode

## 2.0.2 (10050)

* Software Toolz: robot-and-toolbox launcher icon and black/green color theme

## 2.0.1 (10049)

* Software Toolz: own splash screen with the toolbox logo (system and in-app splash)

## 2.0.0 (10048)

* Software Toolz: a second app, built from the same code, with the application id com.software.toolz. It has a bottom bar with Files, Vcode, Disassembler and Decompiler. Vcode itself stays a separate app (com.vcode.studio).
* Files is a file manager: browse, select, copy, cut, paste, rename, delete, compress to ZIP and extract. The tools menu of a file opens text files in the Vcode tab, sends .so libraries and APKs to the Disassembler and Decompiler tabs, and signs APKs.
* The Disassembler and Decompiler tabs list the functions of a library (or of the libraries inside an APK) with search, show the disassembly or the pseudocode, and show info, symbols, strings, imports, exports, xrefs and the call graph.
* Every release has two APKs: Software-Toolz.apk (shown as Software Toolz.apk) and Vcode-foss.apk (shown as Vcode Foss.apk).

## 1.17.0 (10047)

* Cross references and call graph for native ARM64 libraries: new files imports.txt, exports.txt, xrefs.txt (who calls every function and who points to every string) and callgraph.txt, and every function in functions/ and pseudocode/ starts with comment lines for its callers, the functions it calls and the strings it uses.
* Large files open: the app asks for the large heap, and files over 5 million characters open without syntax highlighting, word wrap, bracket and block lines or code completion.

## 1.16.0 (10046)

* Pseudocode is easier to read: the Ghidra type names become C types (uint4 is uint32_t, xunknown8 is uint64_t), calls through the GOT are plain calls (calloc(1,0xb8) instead of (*calloc@got)(1,0xb8)), constants that point to a string get a comment with the text, and the "Removing unreachable block" warnings are one line. Quoted text and comments of the decompiler are never changed.

## 1.15.0 (10045)

* Pseudocode: the library viewer has a new pseudocode folder with a .c file for every function, made by the pure Rust port of the Ghidra decompiler (github.com/Christianzz4603/ghidra-decompiler, Apache-2.0) for ARM64 and ARM libraries. The last opened library is kept in memory so the next function opens faster, and the decompiler runs on a thread with a large stack.
* The Rust library is now built for arm64-v8a and armeabi-v7a only (the x86_64 build is gone to keep the build time and the APK size down).

## 1.14.0 (10044)

* Library viewer: a native library (.so), in the file browser or inside an APK, opens as a folder with info.txt (header and sections), symbols.txt, strings.txt, a functions folder with a file for every function and a code folder with the code in pieces. ARM64 code is disassembled, branch targets get the name of the function they point to, and calls through the PLT are named (for example memcpy@plt). C++ and Rust names are shown readable. Libraries are read only. Other CPU types show the information but not the code yet.
* The disassembler is written in Rust (native/disasm, uses the yaxpeax-arm and object crates). It is built together with the app when cargo, the Rust targets for Android and the NDK are installed, and left out of the build otherwise.

## 1.13.0 (10043)

* Archives and DEX files as folders: in the file browser, tap a zip, jar, apk, aar, war, ear, xapk, apks, aab or dex file and it opens like a folder with the files inside. Open a file to edit it and save it, and Vcode writes the archive again (the first time a copy of the original is kept as .orig). Compiled XML inside an APK, such as AndroidManifest.xml, is shown as text and compiled again when saved. A DEX file shows its classes in folders for the packages, a class opens as smali and a save assembles it back into the DEX file, also when the DEX file is inside an APK. New files and folders, renaming, deleting and copying files in and out of an archive work too. An edited APK has to be signed before it can be installed, which is not part of this version. Binary files (images, resources.arsc) are shown as a note and cannot be saved as text.

## 1.12.0 (10042)

* Edit DEX files: a .dex file opens as a list of its classes. In the command palette (magnifier button), Dex: Open Class lets you search for a class and opens it as smali text, and Dex: Apply Changes to DEX assembles the classes you edited and saved and writes them back into the .dex file (the first time a copy of the original is kept as .orig). Uses smali and baksmali (BSD 3-clause) from Google Maven. Very large .dex files may run out of memory. An APK still has to be unpacked first and signed again after you put the .dex back.

## 1.11.0 (10041)

* Edit compiled XML: a binary AndroidManifest.xml (or other compiled XML) opens as text and is compiled to binary XML again when you save it. Before it writes the file, Vcode decodes the result and compares it with your text, and refuses to save when something would be lost. The first save keeps a copy of the original next to the file (.orig). APK files are still read only, because an edited APK would also need to be signed.

## 1.10.0 (10040)

* Kotlin plugins: a plugin can now ship a classes.dex file with a class that extends VcodePlugin and use the new plugin API (plugin-api module, API version 1) to add commands to the command palette, show text in the status bar, read and change the open file, show dialogs, and add keywords and file types. Plugins declare permissions, Vcode shows them (and a SHA-256 of the file) before installing, and code plugins are off until you turn on Allow code plugins in Settings, Plugins. A Kotlin plugin runs inside Vcode with the same access as the app.
* New sample plugin and a workflow that builds it, see docs/PLUGINS.md.
* Decompiling: a compiled (binary) AndroidManifest.xml, layout or other XML from an APK now opens as readable XML, and opening an .apk shows its decoded AndroidManifest.xml and the list of its files. Resource references are shown as ids (for example @0x7f0b0001). The decoded text cannot be saved over the original, use Save As.
* Fixed: the status bar showed the language scope (for example Source.kotlin) instead of its name.

## 1.9.0 (10039)

* A code editor feel: flat editor tabs with an accent line on the selected one, a breadcrumb bar with the file path under the tabs, a status bar with line and column, selection size, indentation, problems count and language, and a command palette (magnifier button in the toolbar) that searches every editor action.

## 1.8.0 (10038)

* New design language, step 2: settings rows are rounded cards, the file drawer has rounded edges and its file rows have rounded highlights, and menus and popups use bigger corners.

## 1.7.0 (10037)

* New design language, step 1: pill-shaped buttons with sentence-case labels, rounder text fields, dropdowns and dialogs, chip-style tabs with a short pill indicator, flat toolbar, spaced group headings and a new navy and cyan fallback palette (the old orange one is gone).

## 1.6.1 (10036)

* F-Droid friendly build: the Google Play services flavor is gone, so there are no closed-source libraries left; release builds work without a signing key; the APK no longer carries dependency or git metadata.
* New "Reproducibility check" workflow that builds the app twice in different folders and compares the APKs.
* Updated the F-Droid store texts and removed the old screenshots and graphics.

## 1.6.0 (10035)

* Tools bar above the symbol row with Run, Termux, Preview, Output, Problems, AI and Full screen. Long-press a tool and drag to reorder; Hide removes the bar (Tools > Problems and output brings it back).
* The bottom panel can be resized by dragging its top handle and can go full screen. In landscape it starts at half the screen height.
* Run in Termux: runs the open file in the real Termux app (Python, Node.js, Java, Kotlin, C, C++, Go, Rust, PHP, Ruby, Perl, R, Dart, shell) and shows the result in Output.

## 1.5.1 (10034)

* Updates now install over each other: every build is signed with the same permanent key. Builds up to 1.5.0 used a different temporary key each time, so uninstall them once (back up your files first) before installing 1.5.1.

## 1.5.0 (10033)

* OpenRouter AI assistant: add your API key and model in Settings > AI assistant, then ask about the open file from the AI tab of the panel. Replies stream in, with copy and insert at cursor.

## 1.4.0 (10032)

* IntelliSense for JavaScript, TypeScript, Kotlin, Java, Python, HTML, XML and shell: members after a dot, snippets with tab stops, Android layout and manifest tags and attributes.

## 1.3.0 (10031)

* Bottom panel with Problems, Output and Browser preview.
* Run JavaScript and shell scripts.

## 1.2.0 (10030)

* Plugin system: install `.zip` or `.json` plugins with themes, keyword lists and file types.

## 1.1.0 (10029)

* Six VS Code style themes, VS Code style tabs and new editor settings (cursor style, smooth cursor, ligatures, auto save delay).

## 1.0.0 (10028)

* Vcode: rebrand of Squircle CE with 10 new languages, keyword suggestions, a new icon and the `com.vcode.studio` app id.
