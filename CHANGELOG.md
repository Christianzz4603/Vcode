# Changelog

Vcode uses one version per feature or update:

* `versionCode` goes up by 1 for every new feature or update (the base is 10028).
* `versionName` is `1.<n>.0`, where `n` is `versionCode - 10028`.
* A small fix without a new feature can bump only the patch number, for example `1.5.1` with `versionCode` 10034.

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
