# Changelog

Vcode uses one version per feature or update:

* `versionCode` goes up by 1 for every new feature or update (the base is 10028).
* `versionName` is `1.<n>.0`, where `n` is `versionCode - 10028`.
* A small fix without a new feature can bump only the patch number, for example `1.5.1` with `versionCode` 10034.

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
