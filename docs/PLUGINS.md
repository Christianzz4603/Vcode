# Vcode plugins

Plugins add themes, keyword suggestions and file types to Vcode. A plugin is **data only** by default: a plugin file with only themes, keywords and file types
never contains code, so installing one cannot run anything on your phone. A plugin can also
contain Kotlin code, see [Kotlin plugins](#kotlin-plugins-with-code).

## Install a plugin

1. Open **Settings > Plugins**.
2. Tap **Install plugin** and pick a `.zip` or `.json` plugin file.
3. Use the switch to turn a plugin on or off, or tap **Remove** to delete it.

Themes appear in **Settings > Application > Color scheme** right after installing.
Keyword suggestions and file types apply to files you open after installing.

## Plugin format

A plugin is either a single `plugin.json` file, or a `.zip` that contains `plugin.json`
(at the top level, or inside one top-level folder) plus any theme files it uses.

```json
{
  "id": "example.neon-pack",
  "name": "Neon Pack",
  "version": "1.0.0",
  "author": "Your name",
  "description": "A neon theme and a few web keywords",
  "themes": ["themes/neon.json"],
  "keywords": {
    "javascript": ["requestAnimationFrame", "IntersectionObserver"]
  },
  "extensions": {
    ".mjs": "javascript"
  }
}
```

| Field | Required | Notes |
| --- | --- | --- |
| `id` | yes | 3 to 64 characters: letters, digits, `.`, `-`, `_`. Installing the same id again replaces the plugin. |
| `name` | yes | Shown in the plugin list. |
| `version`, `author`, `description` | no | Shown in the plugin list. |
| `themes` | no | List of theme JSON files, relative to `plugin.json`. |
| `keywords` | no | Object: language name to a list of words (up to 5000 per language, each up to 64 characters). |
| `extensions` | no | Object: file extension (with the dot) to a language name. |

At least one of `themes`, `keywords` or `extensions` is required.

### Language names

`keywords` and `extensions` use the language names from
[`languages.json`](../feature-editor/impl/src/main/assets/languages.json), for example
`javascript`, `python`, `kotlin`, `cpp`, `html`, `css`, `markdown`, `swift`, `rust`, `go`.

### Theme files

A theme file uses the same format as the built-in themes. The easiest way to start is to copy
[`vcode_dark.json`](../feature-themes/impl/src/main/assets/themes/vcode_dark.json) and change the colors.

Every theme needs:

* `name` and `type` (`"dark"` or `"light"`)
* a `colors` object with all `global.*` and `editor.*` keys that the built-in themes define, written as `#RRGGBB`
* optional `tokenColors` for syntax highlighting (TextMate scopes)

If a theme is missing a color, the plugin is rejected with a message that names the file and the color.

## Kotlin plugins (with code)

A plugin can ship Kotlin code that hooks into the app through the plugin API.

**Read this first.** A Kotlin plugin runs inside Vcode, with the same access as the app itself:
it can read and change your files and use the network, whatever permissions it declares. The
permissions below only limit what the plugin API gives it, they cannot stop code from using Android
directly. Only install plugins from authors you trust. For that reason code plugins are **off by
default**: turn on **Allow code plugins** in **Settings > Plugins**. When you install one, Vcode shows
its permissions and the SHA-256 of the file before it installs anything.

### plugin.json for a code plugin

```json
{
  "id": "com.example.hello",
  "name": "Hello",
  "version": "1.0.0",
  "author": "You",
  "description": "What it does",
  "apiVersion": 1,
  "permissions": ["commands", "ui"],
  "code": { "dex": "classes.dex", "main": "com.example.HelloPlugin" }
}
```

* `apiVersion`: the plugin API version you built against (this Vcode supports 1).
* `code.main`: a class that extends `VcodePlugin` and has a constructor without parameters.
* `code.dex`: the dex file inside the plugin, usually `classes.dex`.
* `permissions`: any of the values below. A plugin file can also contain themes, keywords and file types as described above.

| Permission | What the plugin API allows |
|---|---|
| `commands` | add entries to the command palette |
| `status.bar` | show text in the status bar |
| `ui` | show toasts and dialogs |
| `editor.read` | read the open file, the selection, the cursor position and the file path |
| `editor.write` | insert, replace the selection or replace the whole text |
| `keywords` | add code completion keywords for a language |
| `file.types` | link file extensions to a language |

A call without the permission throws a `SecurityException` inside the plugin.

### Writing a plugin

The API is one file: [`plugin-api/src/main/kotlin/com/vcode/studio/plugin/api/PluginApi.kt`](../plugin-api/src/main/kotlin/com/vcode/studio/plugin/api/PluginApi.kt).
It is not published as a library yet, so copy that file into your plugin project (or add this
repository as a Gradle module). Vcode always uses its own copy of those classes.

```kotlin
class HelloPlugin : VcodePlugin() {
    override fun onLoad(host: PluginHost) {
        host.registerCommand("hello", "Say hello", Runnable {
            host.ui.toast("Hello from a plugin")
        })
    }
}
```

Rules:

* Only Java classes, Android classes and the classes of the plugin API can be passed between your plugin and Vcode.
  Do not use Kotlin function types there, use `Runnable` and the small interfaces of the API (`TextProvider`, `PromptCallback`, `ConfirmCallback`).
* Your plugin has to contain its own copy of the Kotlin standard library and of any library it uses, because Vcode shrinks and renames its own.
* The language of a file (`host.editor.language`) is a TextMate scope such as `source.kotlin`.
* `onLoad` runs on the main thread when Vcode starts or when the plugin is turned on. Keep it fast.
* Call editor methods from the main thread, for example from a command.

### Build a plugin

[`plugin-sample`](../plugin-sample) is a complete example. It is an Android app module only to get a
`classes.dex` file out of the build. The **Build sample plugin** workflow builds it, packs
`plugin.json` and `classes.dex` into a zip and publishes it on the `sample-plugins` pre-release, so you can
try it on your phone:

```
./gradlew :plugin-sample:assembleDebug
unzip -p plugin-sample/build/outputs/apk/debug/plugin-sample-debug.apk classes.dex > classes.dex
zip my-plugin.zip plugin.json classes.dex
```

### Not possible yet

Plugins cannot add their own screens or Compose UI, new TextMate grammars (new languages and syntax
highlighting), themes from code (a plugin file can still contain theme files) or settings screens.
These are planned for later API versions.

## Limits

* Up to 8 MB per plugin and up to 200 files in a `.zip`.
* File paths inside a `.zip` must stay inside the plugin folder.
* The code of a Kotlin plugin counts towards the 8 MB limit too.

## Examples

See [`examples/plugins`](../examples/plugins):

* `web-extras.json`: a single-file plugin with keywords and file types
* `neon-pack/`: a plugin with a theme. Zip the **contents** of the folder (so `plugin.json` is at the top) and install the `.zip`.
