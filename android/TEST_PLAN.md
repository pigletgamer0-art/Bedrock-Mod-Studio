# Plan de pruebas — Android v0.1 Alpha

Este documento separa lo implementado de lo que todavía necesita prueba en un dispositivo/Emulador Android real.

## Pruebas obligatorias antes de llamar estable a la APK

1. **Arranque offline**
   - Instalar la APK.
   - Activar modo avión.
   - Abrir Bedrock Mod Studio.
   - Deben cargar interfaz, bundle, CSS, Pixel Studio y modelador sin red.

2. **Creación de proyecto**
   - Probar Add-On completo, una sola cosa, Texture Pack, textura suelta y modelo + animación.
   - Reiniciar la Activity/rotar pantalla y confirmar que WebView restaura el estado visible.

3. **Importar PNG**
   - Pixel Studio → Importar PNG.
   - Debe abrir el selector de documentos Android.
   - Elegir 16×16, 64×64 y una imagen de tamaño no estándar.

4. **Importar geometry JSON**
   - Modelador → Importar .geo.json.
   - Elegir un modelo Bedrock válido desde almacenamiento.

5. **Guardar y abrir proyecto**
   - Guardar .bmsproject.json.
   - Confirmar que el archivo se escribe sin corrupción.
   - Volver a importarlo desde el selector Android.

6. **Exportar PNG**
   - Exportar una textura.
   - Android 10+: comprobar `Downloads/Bedrock Mod Studio`.
   - Validar que el PNG abre en una app de galería.

7. **Exportar .mcaddon**
   - Crear un Add-On con BP + RP.
   - Exportar.
   - Debe guardarse y lanzar Minecraft automáticamente cuando `com.mojang.minecraftpe` esté instalado.
   - Minecraft debe iniciar el proceso de importación.

8. **Exportar .mcpack**
   - Probar Behavior Pack y Resource Pack por separado.
   - Confirmar apertura con Minecraft.

9. **Archivo grande**
   - Crear/importar varias texturas para producir un paquete de varios MB.
   - Exportarlo y verificar que el puente por fragmentos no se queda sin memoria ni trunca bytes.

10. **Blockbench**
    - Pulsar “Blockbench ↗”.
    - Debe abrir navegador/Blockbench fuera del WebView y no reemplazar el editor.

11. **Seguridad de navegación**
    - La app debe mantener el editor en `appassets.androidplatform.net`.
    - Navegaciones HTTP/HTTPS externas se abren con Android ACTION_VIEW.

12. **Versiones Android**
    - Mínimo objetivo: Android 7.0 / API 24.
    - Probar especialmente Android 10+ por MediaStore.
    - Probar Android 16 / API 36.

## Estado actual

- XML Android: parseado correctamente.
- Código Java: pasa etapa de parsing; en este contenedor falla después por ausencia de clases del Android SDK, que no está instalado.
- Gradle/Android SDK: pendiente de compilación real.
- GitHub Actions: workflow de build creado; los commits realizados mediante el conector no iniciaron un run automáticamente, por lo que aún no se considera una prueba de compilación.
