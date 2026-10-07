# Bedrock Mod Studio — Android

Primera envoltura nativa de Bedrock Mod Studio para Android.

**Estado:** Android v0.1 Alpha — primera compilación automatizada en GitHub Actions.

## Objetivos de esta etapa

- Ejecutar la aplicación web localmente, sin depender de una web remota.
- Mantener Pixel Studio, modelador, timeline, Creator Lab y editor de código dentro del APK.
- Permitir importación de PNG/JSON/proyectos mediante el selector de archivos Android.
- Interceptar exportaciones Blob de la app web y guardarlas con un puente nativo.
- Guardar en `Downloads/Bedrock Mod Studio` en Android 10+.
- Abrir `.mcaddon`, `.mcpack` y `.mcworld` directamente con Minecraft cuando esté instalado.
- Abrir Blockbench y otros enlaces externos fuera del WebView.

## Stack

- Java 17
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0 en CI
- compileSdk/targetSdk 36
- minSdk 24
- AndroidX Core 1.19.1
- AndroidX WebKit 1.17.1
- `WebViewAssetLoader` para servir los archivos empaquetados desde un origen HTTPS local.

## Web assets

El build no duplica manualmente la web. La tarea `syncWebAssets` copia desde la raíz del repositorio:

- `index.html`
- `styles.css`
- `app.js`
- `manifest.webmanifest`
- `sw.js`
- `bundle/**`

hacia los assets generados del APK.

## Compilar

Desde la raíz del repositorio:

```bash
gradle -p android assembleDebug
```

El APK queda en:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions también construye el APK automáticamente y lo publica como artefacto del workflow.


## UX Android vertical

La app añade una capa específica para teléfono:

- Dock inferior con **Nuevo**, **Diseño**, **Pixel**, **Código** y **Exportar**.
- El dock se oculta cuando se abre el teclado para no tapar el editor.
- `windowSoftInputMode=adjustResize` permite que el WebView reduzca su altura con el teclado.
- El botón Atrás cierra primero el selector de proyecto, luego vuelve a Diseño y solo después sale de la app.
- Android puede enviar directamente a Bedrock Mod Studio archivos `.png`, `.geo.json` y `.bmsproject.json` mediante **Abrir con** o **Compartir**.
- La importación nativa directa tiene un límite de 8 MB para evitar bloquear el WebView; los selectores internos siguen disponibles para los flujos normales.
