# Vcode plugins

Plugins add themes, keyword suggestions and file types to Vcode. A plugin is **data only**:
it never contains code, so installing one cannot run anything on your phone.

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

## Limits

* Up to 8 MB per plugin and up to 200 files in a `.zip`.
* File paths inside a `.zip` must stay inside the plugin folder.

## Examples

See [`examples/plugins`](../examples/plugins):

* `web-extras.json`: a single-file plugin with keywords and file types
* `neon-pack/`: a plugin with a theme. Zip the **contents** of the folder (so `plugin.json` is at the top) and install the `.zip`.
