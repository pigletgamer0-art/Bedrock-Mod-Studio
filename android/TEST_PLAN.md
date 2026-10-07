# Plan de pruebas — Android v0.1 Alpha

Este documento separa lo implementado de lo que todavía necesita prueba en un dispositivo/Emulador Android real.

## Pruebas obligatorias antes de llamar estable a la APK

1. **Arranque offline**
   - Instalar la APK.
   - Activar modo avión.
   - Abrir Bedrock Mod Studio.
   - Deben cargar interfaz, bundle, CSS, Pixel Studio y modelador sin red.

2. **Portrait / interfaz vertical**
   - En un teléfono, abrir la app en vertical.
   - Girar físicamente el teléfono: la Activity debe permanecer en portrait.
   - Verificar que navegación, exportación, Pixel Studio, modelador, UV, timeline, Creator Lab y editor de código sean utilizables sin desplazamiento horizontal global.
   - Confirmar que el selector “Nuevo proyecto” aparece como panel vertical desde la parte inferior.
   - En Android 16 probar también una pantalla de 600dp o más: se declara compatibilidad temporal para conservar las restricciones actuales, pero la interfaz también debe seguir siendo adaptable.

3. **Creación de proyecto**
   - Probar Add-On completo, una sola cosa, Texture Pack, textura suelta y modelo + animación.
   - Reiniciar la Activity y confirmar que WebView restaura el estado visible.

4. **Importar PNG**
   - Pixel Studio → Importar PNG.
   - Debe abrir el selector de documentos Android.
   - Elegir 16×16, 64×64 y una imagen de tamaño no estándar.

5. **Importar geometry JSON**
   - Modelador → Importar .geo.json.
   - Elegir un modelo Bedrock válido desde almacenamiento.

6. **Guardar y abrir proyecto**
   - Guardar .bmsproject.json.
   - Confirmar que el archivo se escribe sin corrupción.
   - Volver a importarlo desde el selector Android.

7. **Exportar PNG**
   - Exportar una textura.
   - Android 10+: comprobar `Downloads/Bedrock Mod Studio`.
   - Validar que el PNG abre en una app de galería.

8. **Exportar .mcaddon**
   - Crear un Add-On con BP + RP.
   - Exportar.
   - Debe guardarse y lanzar Minecraft automáticamente cuando `com.mojang.minecraftpe` esté instalado.
   - Minecraft debe iniciar el proceso de importación.

9. **Exportar .mcpack**
   - Probar Behavior Pack y Resource Pack por separado.
   - Confirmar apertura con Minecraft.

10. **Archivo grande**
    - Crear/importar varias texturas para producir un paquete de varios MB.
    - Exportarlo y verificar que el puente por fragmentos no se queda sin memoria ni trunca bytes.

11. **Blockbench**
    - Pulsar “Blockbench ↗”.
    - Debe abrir navegador/Blockbench fuera del WebView y no reemplazar el editor.

12. **Seguridad de navegación**
    - La app debe mantener el editor en `appassets.androidplatform.net`.
    - Navegaciones HTTP/HTTPS externas se abren con Android ACTION_VIEW.

13. **Versiones Android**
    - Mínimo objetivo: Android 7.0 / API 24.
    - Probar especialmente Android 10+ por MediaStore.
    - Probar Android 16 / API 36.
    - En dispositivos grandes Android 16 puede aplicar reglas adaptativas propias; la UI no debe romperse aunque el sistema permita otra orientación.

## Estado actual

- XML Android: parseado correctamente.
- Portrait: declarado en manifest y reforzado en `MainActivity`.
- UI Android: tiene reglas específicas `.android-native` para portrait.
- Código Java: pendiente de compilación completa en un entorno con Android SDK.
- Gradle/Android SDK: pendiente de compilación real.
- GitHub Actions: workflow de build creado; los commits realizados mediante el conector no iniciaron un run automáticamente, por lo que aún no se considera una prueba de compilación.


## Pruebas de UX nativa añadidas

- Abrir el teclado dentro de Código y confirmar que el dock inferior desaparece y el textarea sigue visible.
- Pulsar Atrás desde Pixel/Código: debe volver a Diseño antes de cerrar la app.
- Pulsar Atrás con el modal de proyecto abierto: debe cerrar el modal.
- Desde Archivos/Galería, usar **Abrir con** o **Compartir** con un PNG y comprobar que llega a Pixel Studio.
- Repetir con un `.geo.json` y un `.bmsproject.json`.
- Intentar enviar un archivo no compatible y confirmar que la app avisa sin fallar.


## Edge-to-edge / barras del sistema

- Android 15 y 16: confirmar que la barra de estado, cámara/notch y barra de navegación no cubren botones ni el dock.
- Probar navegación por gestos y navegación de 3 botones.
- Abrir el teclado en Código y confirmar que los insets del sistema no producen doble margen inferior.
- Probar un dispositivo con recorte/cutout si está disponible.


## Autosave nativo

- Crear/modificar un proyecto y esperar al menos 45 segundos.
- Cerrar la app desde recientes y volver a abrirla: debe recuperar el último borrador interno.
- Mandar la app al fondo y volver: no debe aparecer una descarga visible por cada autosave.
- Confirmar que Guardar proyecto manual sigue exportando el archivo normalmente.
- Probar un proyecto cercano a 8 MB: si supera el límite de recuperación directa, la app no debe bloquearse.


## Detección y entrega a Minecraft

- Con Minecraft instalado, exportar .mcaddon y .mcpack: debe intentar abrir Minecraft directamente.
- Repetir con .mcworld y .mctemplate cuando el editor soporte esos tipos.
- Sin Minecraft instalado, la exportación debe conservar el archivo y mostrar el selector de apps/fallback.
- Confirmar que la URI se comparte con permiso temporal de lectura y que Minecraft puede leer el ZIP.


## Panel de proyecto desplegable

- Al arrancar en teléfono, el panel lateral debe estar cerrado.
- Pulsar “☰ Proyecto”: debe abrirse como panel flotante sin cambiar a horizontal.
- Pulsar Atrás con el panel abierto: debe cerrarlo antes de cambiar de pestaña o salir.
- En pantallas angostas el botón puede reducirse al icono ☰ y no debe cubrir las pestañas.


## Aislamiento offline del WebView

- Con modo avión, toda la interfaz debe cargar normalmente desde appassets.
- Un recurso HTTP/HTTPS incrustado desde el editor debe quedar bloqueado dentro del WebView.
- Los botones que abren Blockbench o una web externa deben seguir enviando la URL al navegador mediante ACTION_VIEW.
- Confirmar que ninguna página web externa se carga dentro del WebView que tiene acceso a AndroidBridge.


## Inicio de creaciones y botón +

- En Diseño, comprobar que aparece primero la tarjeta “Lo que has creado”.
- El botón + debe abrir una hoja inferior ordenada por Contenido del juego, Gameplay y lógica, Visual y Proyecto.
- Crear objeto, bloque y entidad desde la hoja y confirmar que aparecen inmediatamente en “Lo que has creado”.
- Crear receta, loot y spawn desde la hoja y confirmar que abren Creator Lab y luego aparecen en la lista al guardar.
- Abrir Textura, Modelo 3D y Animación desde la hoja y confirmar que navegan a la herramienta correcta.
- Guardar una textura PNG y confirmar que aparece bajo “Texturas guardadas”.
- Pulsar “Nuevo proyecto” desde la hoja y confirmar que abre el selector de tipo de proyecto existente.
- Pulsar Atrás con la hoja + abierta: debe cerrarla antes de cualquier otra navegación.
- El antiguo bloque duplicado de “Crear elemento” no debe mostrarse en Android.


## Buscador de creaciones

- Crear al menos tres tipos distintos de contenido.
- El contador junto al buscador debe reflejar el total.
- Buscar por nombre o texto visible y confirmar que solo quedan coincidencias.
- El contador debe cambiar a visible/total durante una búsqueda.
- Una búsqueda sin coincidencias debe mostrar “No encontré coincidencias”.
- Borrar el texto debe restaurar toda la lista sin perder ni reordenar contenido.


## Biblioteca local de proyectos

- Guardar manualmente un .bmsproject.json: debe seguir exportándose a Descargas y aparecer también en “Mis proyectos”.
- Guardar el mismo nombre otra vez: debe reemplazar la copia interna, no duplicarla.
- Cerrar y abrir la app: la biblioteca debe conservarse.
- Tocar un proyecto: debe cargarlo usando el mismo importador de proyectos.
- Borrar una copia interna: debe desaparecer de “Mis proyectos” sin borrar el archivo exportado en Descargas.
- El listado debe ordenar los proyectos por fecha de modificación más reciente.
- Un proyecto interno mayor a 12 MB no debe cargarse directamente al WebView.


## Importar .mcpack / .mcaddon

- Desde Archivos Android, usar Abrir con Bedrock Mod Studio sobre un .mcpack.
- Detectar Behavior Pack/Resource Pack mediante manifest.json y convertir a proyecto v0.6.
- Abrir .mcaddon con BP + RP internos y conservar ambos bajo BP/ y RP/.
- Reconocer items, blocks, entities, recipes, loot, spawn, script y modelos en “Lo que has creado”.
- Conservar texturas/modelos/sonidos como binarios.
- Rechazar rutas ../, demasiadas entradas, >16 MB por archivo o >64 MB descomprimidos.
- Guardar el proyecto convertido y volver a abrirlo desde “Mis proyectos”.


## Botón Importar Add-On

- Abrir el botón + y tocar “Importar Add-On”.
- Debe aparecer el selector de documentos Android sin salir de Bedrock Mod Studio.
- Elegir un .mcpack y un .mcaddon válidos: ambos deben pasar por el importador seguro y abrirse como proyecto.
- Elegir un archivo con otra extensión: la app debe rechazarlo con mensaje y continuar funcionando.
- Cancelar el selector: la app debe volver sin cambiar el proyecto actual.
