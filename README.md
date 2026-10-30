# JavaFX Companion

Plugin independiente para Eclipse que integra Scene Builder 26 en un editor FXML con dos pestañas y muestra los FXML junto a sus controladores Java en Project Explorer.

**Estado:** versión inicial en desarrollo (0.3.3). **Plataforma:** Windows x64, Eclipse con JDT y Java 24 o posterior; probado con Eclipse 2026-12 M1 y Java 25. Los controles personalizados del proyecto todavía no se cargan automáticamente.

El repositorio contiene el código y los proyectos PDE. Las bibliotecas JavaFX/Scene Builder, los instalables generados y los archivos locales de pruebas están excluidos de Git. Los scripts de compilación actuales usan rutas del entorno original: hay que adaptarlas para compilar en otra máquina.

Licencia del código: MIT. Bibliotecas e iconos: ver [avisos de terceros](third-party/NOTICE.md).

Plugin PDE independiente para Eclipse. No depende de CodexIDE, e(fx)clipse ni del SDK JavaFX.

## Editor integrado (0.3.0)

Abrir un FXML con Open With > FXML + Scene Builder. El editor tiene Código FXML y Diseño. La pestaña Diseño usa Scene Builder Kit 26 con biblioteca de controles, jerarquía, lienzo e inspector. Se sincroniza al entrar en Diseño; las ediciones visuales actualizan el documento y se guardan con Ctrl+S. Ctrl+Z/Ctrl+Y dentro del diseñador usan su historial. Si el FXML es inválido, se conserva el código y se muestra el error en lugar de un diseño anterior.

Requiere Windows x64 y que Eclipse se ejecute con Java 24 o posterior (probado con Java 25). JavaFX 26 y Scene Builder están incluidos en el instalable; no cambia la versión JavaFX de los proyectos. La pestaña de código reutiliza el editor FXML de e(fx)clipse si está disponible; en caso contrario ofrece texto plano.

Para establecerlo como editor habitual: Preferences > General > Editors > File Associations > *.fxml > FXML + Scene Builder > Default. Cerrar y reabrir los FXML ya abiertos.

Esta primera integración se centra en controles JavaFX estándar y recursos relativos al FXML. No configura automáticamente las dependencias del proyecto como bibliotecas de controles personalizados. El diseñador puede reformatear el XML al editarlo.

## Project Explorer (0.2.0)

Los FXML con `fx:controller` resoluble aparecen en el paquete Java del controlador dentro de Project Explorer. Desde 0.2.2 también permanecen visibles en su carpeta original de resources: ambas entradas abren el mismo archivo, sin copiarlo ni moverlo. Los recursos restantes y FXML sin controlador resoluble permanecen donde estaban.

La contribución está activada por defecto. Se puede activar o desactivar en el menú de Project Explorer > Filters and Customization (o Customize View) > Content > JavaFX: FXML junto a sus controladores. Requiere que el contenido Java de JDT esté activo. No modifica Package Explorer. Las carpetas originales pueden permanecer visibles aunque ya no muestren FXML.

## Desarrollo

1. File > Import > General > Existing Projects into Workspace.
2. Seleccionar `D:\eclipse\repos\JavaFXCompanion`.
3. Usar como plataforma destino un Eclipse con JDT y PDE (la instalación local los incluye).
4. Run As > Eclipse Application desde el proyecto.
5. En el Eclipse de prueba: Window > Show View > Other > JavaFX > JavaFX Controllers.

La vista agrupa por proyecto y paquete los controladores Java y sus FXML. Abre los archivos con doble clic y actualiza las asociaciones tras cambios en el workspace. Los archivos mantienen su ubicación original. Varios FXML pueden compartir controlador; sus rutas permiten distinguir nombres repetidos.

Los FXML sin `fx:controller`, con XML incompleto o con clases que JDT no puede resolver aparecen en un grupo separado. Se omiten recursos derivados y carpetas ocultas. Los controladores configurados únicamente desde Java mediante `setController` no se infieren.

La vista independiente JavaFX Controllers se conserva como alternativa y solo muestra clases vinculadas a FXML. La integración principal desde 0.2.0 funciona en Project Explorer.

## Compilación local

Ejecutar `tools/build.ps1`. Compila contra los bundles activos de la instalación local y genera un JAR en `build`. Para preparar las dependencias en una copia nueva, `tools/prepare-editor.ps1` toma el SDK de `D:\eclipse\tools\javafx-26\javafx-sdk-26` y el JAR instalado de Scene Builder 26; incluye las licencias del SDK. Requiere JDK 24+ para compilar.

## Generar e instalar el distribuible

Ejecutar `tools/release.ps1`: compila, asigna una versión, empaqueta el feature, genera los metadatos p2 con el publisher de Eclipse y crea un ZIP en `D:\eclipse\releases\javafxcompanion`.

En Eclipse: Help > Install New Software > Add > Archive, seleccionar el ZIP generado y desmarcar «Group items by category» si no aparece la lista. Seleccionar JavaFX Companion, continuar y reiniciar. Abrir Window > Show View > Other > JavaFX > JavaFX Controllers.

El ZIP es un repositorio p2 instalable, no un ejecutable independiente. Requiere Eclipse con JDT. La generación del paquete no instala el plugin en el IDE principal.

## Verificación

`tools/test.ps1` ejecuta pruebas dentro de Equinox con un workspace de prueba bajo `build`: resuelve una clase con JDT, verifica la contribución registrada, inserta el FXML en el paquete virtual, conserva recursos no asociados y comprueba la invalidación al cambiar el FXML. No sustituye la prueba visual del explorador.

`tools/test.ps1 -Visual` abre y captura los paneles de Scene Builder sobre FXCanvas y comprueba la carga/serialización de FXML. `tools/test.ps1 -Editor` abre un workbench aislado y verifica ambas direcciones de sincronización, edición mediante un job de Scene Builder, estado modificado, guardado y conservación de FXML inválido. Ninguna prueba usa el workspace principal.

Las pruebas deben usar un área p2 y perfil propios (`build/isolated-p2`, `JavaFXCompanionIsolatedTests`), además del workspace separado. No basta con cambiar `-data` y `-configuration`: copiar el `config.ini` sin cambiar `eclipse.p2.data.area` y `eclipse.p2.profile` hace que el reconciliador de Eclipse modifique el perfil real.
