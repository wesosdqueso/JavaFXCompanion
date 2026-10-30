# JavaFX Companion

An independent Eclipse PDE plugin that embeds Scene Builder 26 in a dual-page FXML editor and displays FXML files alongside their Java controllers in Project Explorer.

**Status:** early development (0.3.3). **Requirements:** Windows x64, Eclipse with JDT, and Java 24 or later. Tested with Eclipse 2026-12 M1 and Java 25.

The repository contains source code and PDE projects. Generated libraries, installers, and local test data are excluded from Git. The build scripts currently use paths from the original development environment; adjust them before building on another machine.

Project code is licensed under MIT. See [third-party notices](third-party/NOTICE.md) for library and icon licenses.

## Embedded FXML editor

Right-click an FXML file and select **Open With > FXML + Scene Builder**. The editor provides two pages:

- **FXML Source:** reuses the e(fx)clipse FXML editor when available, with a plain-text fallback.
- **Design:** embeds Scene Builder Kit 26 with a control library, hierarchy, canvas, and property inspector.

Switching to Design loads the current source. Visual edits update the document and can be saved with Ctrl+S. Ctrl+Z and Ctrl+Y use the designer's undo history while working in Design. Invalid FXML is preserved and an error replaces the previous design.

JavaFX 26 and Scene Builder are bundled in the installer. They do not change the JavaFX version used by your projects. CodexIDE and e(fx)clipse are not required.

To choose the default editor, use **Preferences > General > Editors > File Associations > *.fxml > FXML + Scene Builder > Default**. Close and reopen existing FXML tabs. Version 0.3.3 also performs a one-time migration of editor associations for FXML files in open projects.

This initial integration supports standard JavaFX controls and resources relative to the FXML file. Project dependencies are not automatically imported as custom-control libraries. Visual editing may reformat the XML.

## Project Explorer integration

FXML files with a resolvable `fx:controller` appear in their controller's Java package. They remain visible in their original resources folder as well. Both entries open the same file; files are never copied or moved. Java and FXML files are sorted together by base name, with the Java class first.

The integration is enabled by default. Toggle it under **Project Explorer > Filters and Customization (or Customize View) > Content > JavaFX: FXML alongside controllers**. JDT Java content must also be enabled. Package Explorer is not modified.

The optional **JavaFX Controllers** view groups controllers and their FXML files by project and package. Open it through **Window > Show View > Other > JavaFX > JavaFX Controllers**. Double-click to open files; the view refreshes after workspace changes. Paths distinguish FXML files with duplicate names.

In the standalone view, FXML files with missing controllers, incomplete XML, or unresolved Java types appear under **FXML without resolved controller**. Derived resources and hidden folders are skipped. Controllers assigned exclusively through Java code using `setController` are not inferred.

## Development

1. Import the repository using **File > Import > General > Existing Projects into Workspace**.
2. Select an Eclipse target platform with JDT and PDE.
3. Prepare the local dependencies described below.
4. Select **Run As > Eclipse Application** from the plugin project.
5. In the runtime workbench, open an FXML file with **FXML + Scene Builder**.

### Local build

Run `tools/build.ps1` to compile against the bundles in the configured local Eclipse installation and create a plugin JAR under `build`.

For a fresh checkout, `tools/prepare-editor.ps1` copies JavaFX SDK 26 and the installed Scene Builder 26 JAR into the generated dependency folders, including JavaFX license notices. Update the SDK, Scene Builder, Eclipse, and JDK paths in the scripts to match your environment. JDK 24 or later is required.

### Package and install

Run `tools/release.ps1` to compile, assign a version, package the feature, generate p2 metadata using Eclipse's publisher, and create an installable ZIP. The current output location is configured in `tools/package-release.py`.

In Eclipse, select **Help > Install New Software > Add > Archive**, choose the generated ZIP, and select **JavaFX Companion**. If the list is empty, uncheck **Group items by category**. Complete the installation and restart Eclipse.

The ZIP is a p2 repository for Eclipse, not a standalone application. Packaging does not install the plugin into the development IDE.

## Verification

- `tools/test.ps1`: runs association checks in Equinox, including JDT controller lookup, navigator registration, virtual package entries, filename sorting, and cache invalidation.
- `tools/test.ps1 -Visual`: opens Scene Builder panels on FXCanvas and checks FXML loading and serialization.
- `tools/test.ps1 -Editor`: opens a test workbench and checks source/design synchronization, visual editing, dirty state, saving, and preservation of invalid source.

Tests use a separate workspace, configuration, p2 data area (`build/isolated-p2`), and profile (`JavaFXCompanionIsolatedTests`). Keep all four isolated: changing only `-data` and `-configuration` while retaining the main p2 settings can modify the real installation profile.
