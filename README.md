# JavaFX Companion

JavaFX Companion adds an embedded Scene Builder editor and FXML navigation to Eclipse.

**Requirements** - Windows x64, Eclipse with JDT, and Java 24 or later. Tested with Eclipse 2026-12 M1 and Java 25.

## Install

1. Download the [JavaFX Companion 0.3.4 ZIP](https://github.com/wesosdqueso/JavaFXCompanion/releases/download/v0.3.4/JavaFX-Companion-0.3.4-eclipse-win-x64.zip) and leave it compressed.
2. In Eclipse, go to **Help > Install New Software > Add > Archive** and select the ZIP.
3. Select **JavaFX Companion**, finish the installation, and restart Eclipse. If the list is empty, clear **Group items by category**.

JavaFX 26 and Scene Builder are included. e(fx)clipse is optional.

## Features

- **FXML editing** - Open a file with **Open With > FXML + Scene Builder** and switch between source and visual design in the same editor. Visual changes update the document. The source tab uses e(fx)clipse when installed and falls back to plain text.
- **Custom controls** - The designer loads compiled controls and dependencies from the Eclipse Java build path. Build the project before opening **Design**.
- **FXML next to controllers** - Project Explorer shows FXML files in their controller's Java package, with a matching package path as a fallback. Both entries open the original file from its resources folder.

## Development

1. Import the repository using **File > Import > General > Existing Projects into Workspace**.
2. Select an Eclipse target platform with JDT and PDE.
3. Prepare the local dependencies described below.
4. Select **Run As > Eclipse Application** from the plugin project.
5. In the runtime workbench, open an FXML file with **FXML + Scene Builder**.

### Local build

Run `tools/build.ps1` to compile against the bundles in the configured local Eclipse installation and create a plugin JAR under `build`.

For a fresh checkout, `tools/prepare-editor.ps1` copies JavaFX SDK 26 and the installed Scene Builder 26 JAR into the generated dependency folders, including JavaFX license notices. Update the SDK, Scene Builder, Eclipse, and JDK paths in the scripts to match your environment. JDK 24 or later is required.

### Package a release

Run `tools/release.ps1` to compile, assign a version, package the feature, generate p2 metadata using Eclipse's publisher, and create an installable ZIP. The current output location is configured in `tools/package-release.py`.

### Automatic local updater

Run `tools/build-update-launcher.ps1` once to create `build/Actualizar-JavaFXCompanion.exe`.
Double-click that executable to compile the current sources, prepare a versioned p2
repository, wait for Eclipse to close normally, install JavaFX Companion, and reopen
`D:\eclipse\workspaces\2025`. Save your work and close Eclipse when prompted;
the updater does not terminate Eclipse for you. It updates JavaFX Companion only.

The launcher accepts `--workspace`, `--eclipse`, `--java`, and `--repo` path overrides.
Use `--check` to validate paths without building or installing. Regenerate the
launcher with `-Workspace`, `-EclipseHome`, and `-JavaHome` to change its defaults.
Keep the executable in this repository's `build` directory.

`tools/install-live.ps1 -PrepareOnly` compiles and publishes a local update site
without changing the Eclipse installation. Its publisher uses a separate configuration,
workspace, p2 data area, and profile. Output is stored in `build/update-site-*`;
publisher and installer logs are in the matching `build/release-*` directory.
To install from a terminal, run `tools/install-live.ps1 -WaitForExit -Restart -Workspace <path>`.
The installer checks the installed bundle version before reopening Eclipse.
Rollback is available under **Help > About Eclipse IDE > Installation Details >
Installation History > Revert**.

## Verification

- `tools/test.ps1`: runs association checks in Equinox, including JDT controller lookup, navigator registration, virtual package entries, filename sorting, and cache invalidation.
- `tools/test.ps1 -Visual`: opens Scene Builder panels on FXCanvas and checks FXML loading and serialization.
- `tools/test.ps1 -Editor`: opens a test workbench and checks source/design synchronization, visual editing, dirty state, saving, and preservation of invalid source.

Tests use a separate workspace, configuration, p2 data area (`build/isolated-p2`), and profile (`JavaFXCompanionIsolatedTests`). Keep all four isolated: changing only `-data` and `-configuration` while retaining the main p2 settings can modify the real installation profile.

## License

Project code is licensed under MIT. See [third-party notices](third-party/NOTICE.md) for bundled library and icon licenses.
