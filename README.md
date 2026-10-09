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
* A GitHub Actions workflow that builds the FOSS APK and publishes it on the releases page

## Download

Get the latest `Vcode-foss.apk` from the [website](https://vcode-site.pages.dev) or the [Releases](https://github.com/Christianzz4603/Vcode/releases) page.

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
3. Create a `local.properties` file with the following properties:  
   `KEYSTORE_PATH`: path to the keystore file (relative to the `app` directory)  
   `KEYSTORE_PASSWORD`: password for the keystore  
   `KEY_ALIAS`: key alias that will be used to sign the app  
   `KEY_PASSWORD`: key password  
   **Warning**: keep this file safe and make sure nobody, except you, has access to it.
4. Open the project in **[Android Studio](https://developer.android.com/studio/)** or build from the
   command line: `./gradlew :app:assembleFossRelease`

The **Release FOSS APK** workflow does the same on GitHub: it runs on every push to `main`, signs the
APK with the keystore secrets (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) if they
are set, and publishes `Vcode-foss.apk` to the `foss-latest` release.

#### Available flavors

* `foss`: a flavor without closed-source libraries
* `gms`: a flavor with Google Play services (in-app updates)

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
