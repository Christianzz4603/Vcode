# Vcode

**Vcode** is a modified version of [Squircle CE](https://github.com/massivemadness/Squircle-CE) - a fast and free multi-language code editor and file manager for Android.

Vcode is based on Squircle CE by Blacksquircle and is distributed under the same Apache 2.0 license.

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Website: [vcode-site.pages.dev](https://vcode-site.pages.dev)

## What is different from Squircle CE

* New name, icon and splash screen (application id `com.vcode.studio`)
* 10 additional languages with syntax highlighting: Swift, R, PowerShell, Objective-C, Less, SCSS, CoffeeScript, Pug, Diff and Handlebars (49 languages in total)
* Keyword suggestions while typing, loaded from simple JSON files in `feature-editor/impl/src/main/assets/keyword/`
* 6 new VS Code-style themes: Vcode Dark (the new default), Dark+, Light+, One Dark, GitHub Dark and Dracula (15 themes in total)
* VS Code-style tabs with a language icon and a dot for unsaved changes
* New editor settings: cursor style, smooth cursor animation, font ligatures and an auto save delay
* Plugin system: install `.zip` or `.json` plugins to add themes, keyword suggestions and file types (Settings > Plugins), and, if you allow it, Kotlin plugins that use a plugin API for commands, status bar items and editor access
* Decompiling and editing: open a compiled AndroidManifest.xml (or other binary XML from an APK) as readable XML, edit it and save it back as binary XML, or open an `.apk` to see its decoded manifest and file list
* DEX editing: open a `.dex` file, pick a class from the command palette, edit it as smali and apply the changes back into the file (uses smali and baksmali)
* New Vcode design language: pill buttons, chip tabs, rounder fields and dialogs, flat toolbar and a navy and cyan palette
* A GitHub Actions workflow that builds the FOSS APK and publishes it on the releases page
* F-Droid friendly build: no Google Play services and no closed-source libraries, release builds work without a signing key, and a workflow that builds the app twice and compares the results (see [docs/FDROID.md](docs/FDROID.md))

## Download

Get the latest `Vcode-foss.apk` from the [website](https://vcode-site.pages.dev) or the [Releases](https://github.com/Christianzz4603/Vcode/releases) page.

[![Get it on Orion Store](https://raw.githubusercontent.com/RookieEnough/Orion-Store/refs/heads/main/assets/orion-badge.png)](https://rookieenough.github.io/Orion-Data/redirect.html?id=vcode)

## Plugins

Vcode can load plugins from **Settings > Plugins**: data-only plugins (themes, keyword lists and file types) and, once you turn on **Allow code plugins**, Kotlin plugins that use the [plugin API](plugin-api). A Kotlin plugin runs inside Vcode with the same access as the app, so only install ones you trust. See the [plugin guide](docs/PLUGINS.md) and the [examples](examples/plugins).

## Build instructions

### Prerequisites

* JDK 17
* About **1,1GB** of free disk space and **4GB** of RAM
* **macOS** or **Linux**-based operating system. **Windows** is supported by
  using [Git Bash](https://gitforwindows.org/).

### Building

1. `$ git clone --recursive --depth=1 --shallow-submodules https://github.com/Christianzz4603/Vcode Vcode`
2. In case you forgot the `--recursive` flag, `cd` into the `Vcode` directory
   and: `$ git submodule init && git submodule update --init --recursive --depth=1`
3. Optional, to sign the APK: create a `local.properties` file with the following properties:  
   `KEYSTORE_PATH`: path to the keystore file (relative to the `app` directory)  
   `KEYSTORE_PASSWORD`: password for the keystore  
   `KEY_ALIAS`: key alias that will be used to sign the app  
   `KEY_PASSWORD`: key password  
   **Warning**: keep this file safe and make sure nobody, except you, has access to it.  
   Without this file the build still works and produces an unsigned APK (`app-foss-release-unsigned.apk`).
4. Open the project in **[Android Studio](https://developer.android.com/studio/)** or build from the
   command line: `./gradlew :app:assembleFossRelease`

The **Release FOSS APK** workflow does the same on GitHub: it runs on every push to `main`, signs the
APK with the keystore secrets (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) if they
are set, and publishes `Vcode-foss.apk` to the `foss-latest` release.

#### Flavor

* `foss`: the only flavor, without closed-source libraries and without Google Play services

See [docs/FDROID.md](docs/FDROID.md) for F-Droid, reproducible builds and the build recipe.

## Credits and license

Vcode is built on [Squircle CE](https://github.com/massivemadness/Squircle-CE) by Blacksquircle and its
contributors, and on the [sora-editor](https://github.com/Rosemoe/sora-editor) component library.
See [NOTICE](NOTICE) for the list of modifications.

```
Copyright Squircle CE contributors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
