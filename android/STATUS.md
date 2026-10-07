# Android — camino al 100%

Fecha: 2026-10-07

## Definición de 100%

Android solo se marca 100% cuando se cumplan TODOS estos gates:

- [x] Proyecto Android nativo creado.
- [x] Interfaz portrait-first.
- [x] Dock táctil + drawer de proyecto.
- [x] Importación Android de PNG, geo.json y bmsproject.json.
- [x] Exportación nativa por fragmentos.
- [x] Guardado en Downloads/Bedrock Mod Studio.
- [x] Handoff de mcaddon/mcpack/mcworld/mctemplate a Minecraft con fallbacks.
- [x] Autosave nativo interno.
- [x] Edge-to-edge / notch / system bars.
- [x] WebView aislado para funcionamiento offline.
- [ ] CI llega al compilador Android.
- [ ] APK debug compila.
- [ ] APK se instala y arranca en dispositivo real.
- [ ] Prueba offline real.
- [ ] Importación/exportación real en Android.
- [ ] Minecraft confirma importación de mcaddon y mcpack.
- [ ] Pruebas de teclado, Atrás, rotación y recuperación de borrador.
- [ ] Corrección de todos los bugs encontrados en esas pruebas.
- [ ] Build candidato final reproducible.

## Estado medible

La mayoría de las funciones Android ya están implementadas en código, pero el porcentaje verificado todavía es menor porque no se ha logrado llegar al compilador.

**Implementación de funciones:** aproximadamente 80%.
**Verificación real end-to-end:** aproximadamente 55–60%.

No se debe reportar 100% hasta tener APK y pruebas reales.

## Bloqueo CI actual

El workflow activo usa:

`android-actions/setup-android@v3`

En el runner actual, esa versión ejecuta:

`sdkmanager tools`

El paquete histórico `tools` ya no está disponible y la acción termina antes de instalar SDK 36 o ejecutar Gradle.

El log real muestra:

- `Warning: Failed to find package 'tools'`
- `setup-android@v3`
- build de Gradle: **no ejecutado**

## Workflow corregido

El archivo `android/CI_WORKFLOW_FIXED.yml` contiene el reemplazo preparado:

- actions/checkout@v7
- actions/setup-java@v6
- android-actions/setup-android@v4
- packages: platform-tools
- SDK 36 + build-tools 36.0.0
- Gradle 9.6
- assembleDebug
- artifact APK

El conector actual puede escribir código normal del repositorio, pero GitHub bloquea la actualización directa del archivo protegido `.github/workflows/android.yml`. Por eso se conserva aquí la versión exacta que debe reemplazarlo cuando exista permiso de workflows.
