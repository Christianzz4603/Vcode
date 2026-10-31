# F-Droid and reproducible builds

Vcode is built so that F-Droid (and anybody else) can rebuild the APK from the source code.

## What the build guarantees

* Only the `foss` flavor exists. There is no Google Play services flavor and no closed-source library in the Gradle files.
* Dependencies come from Maven Central, Google's Maven repository, the Gradle plugin portal and JitPack (JitPack builds Termux and libsu from their public source code).
* The Kotlin/Java sources of the editor library (`sora-editor`) come in as a Git submodule, so clone with `--recursive`.
* `./gradlew :app:assembleFossRelease` works without any secrets. If `local.properties` has no `KEYSTORE_PATH`, the release APK is left unsigned (`app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk`).
* The APK carries no Google-signed dependency metadata and no git information (`dependenciesInfo` and `vcsInfo` are switched off in `app/build.gradle.kts`).
* The `Reproducibility check` workflow (Actions tab) builds the unsigned APK twice, from two differently named folders, and compares the two files. The result of each run is in its log.

The build uses JDK 17 and the Gradle wrapper that is in the repository.

## Rust and the NDK (library viewer)

The library viewer is written in Rust and lives in `native/disasm`. Its dependencies are free software (yaxpeax-arm under 0BSD, `object`, `cpp_demangle`, `rustc-demangle` and `jni` under Apache-2.0 or MIT) and `Cargo.lock` is in the repository.

* `core-common/build.gradle.kts` builds it for `arm64-v8a` and `armeabi-v7a` when `cargo`, the Rust targets `aarch64-linux-android` and `armv7-linux-androideabi`, and the Android NDK (`ANDROID_NDK_HOME`) are all available.
* The pseudocode comes from [ghidra-decompiler](https://github.com/Christianzz4603/ghidra-decompiler), a pure Rust port of the Ghidra decompiler (Apache-2.0). It is a git dependency that is pinned to a commit in `native/disasm/Cargo.toml` and `Cargo.lock`. F-Droid builds without network access, so the crates have to be vendored (`cargo vendor`) for an F-Droid build.
* When one of them is missing, the build leaves the library out and still succeeds, the library viewer then tells you that it is not part of the build.
* The F-Droid build server has to provide Rust and the NDK for the library viewer to be included. Ask the fdroiddata maintainers how Rust apps get these in the recipe before you submit.
* The **Reproducibility check** workflow installs the Rust targets too. On the 1.14.0 branch it built two identical unsigned APKs (sha256 `3bc8d8f87da2c0abf51c7ba3b90a1a33cf7bd93395205f067f29418e276e10ce`), with the Rust library inside.

## Release tags

The **Release FOSS APK** workflow publishes `Vcode-foss.apk` for every tag that starts with `v`, for example `v1.14.0`. Create the tag only when `versionName` and `versionCode` in `app/build.gradle.kts` match it (`1.14.0` and `10044`).

## Anti-feature to declare

The optional AI assistant sends the text you ask about to the OpenRouter service, which is not free software. F-Droid lists this as the `NonFreeNet` anti-feature.

## Draft recipe for fdroiddata

This is a starting point for `metadata/com.vcode.studio.yml` in a merge request to [fdroiddata](https://gitlab.com/fdroid/fdroiddata). F-Droid's own checks decide the final form, so run `fdroid lint` and `fdroid build com.vcode.studio` first.

```yaml
Categories:
  - Development
License: Apache-2.0
WebSite: https://vcode-site.pages.dev
SourceCode: https://github.com/Christianzz4603/Vcode
IssueTracker: https://github.com/Christianzz4603/Vcode/issues
Changelog: https://github.com/Christianzz4603/Vcode/blob/HEAD/CHANGELOG.md

AntiFeatures:
  NonFreeNet:
    en-US: The optional AI assistant sends your prompts to the OpenRouter service.

AutoName: Vcode

RepoType: git
Repo: https://github.com/Christianzz4603/Vcode.git
Binaries: https://github.com/Christianzz4603/Vcode/releases/download/v%v/Vcode-foss.apk

Builds:
  - versionName: 1.14.0
    versionCode: 10044
    commit: v1.14.0
    subdir: app
    submodules: true
    gradle:
      - foss

AllowedAPKSigningKeys: <SHA-256 of the signing certificate>

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.14.0
CurrentVersionCode: 10044
```

Get the certificate fingerprint for `AllowedAPKSigningKeys` from a released APK:

```
apksigner verify --print-certs Vcode-foss.apk
```

Use the lowercase `SHA-256 digest` value. `Binaries` and `AllowedAPKSigningKeys` only matter if you want F-Droid to publish your own signed APK after it checked that its build matches. Leave both out to let F-Droid sign its own build.

## Orion Store

The release asset is always named `Vcode-foss.apk`.
