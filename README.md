# Bedrock Mod Studio v0.6 Alpha

Editor offline-first para crear contenido de Minecraft Bedrock desde navegador/PWA. Puede trabajar como Add-On completo o como herramienta enfocada para una sola pieza, un Texture Pack, una textura PNG o un modelo con animaciones.

## Novedades v0.6 — Creator Lab

La v0.6 añade tres generadores visuales que escriben archivos reales del Behavior Pack:

- **Recetas shaped y shapeless**: cuadrícula 3×3, resultado, cantidad y generación de `BP/recipes/*.json`.
- **Loot tables**: número de tiradas, entradas ponderadas y generación de `BP/loot_tables/*.json`.
- **Spawn rules**: identificador de entidad, population control, superficie/subterráneo/agua, peso, luz y tamaño de grupo; genera `BP/spawn_rules/*.json`.
- El Inspector revisa también la estructura básica de recetas, loot tables y spawn rules.
- Estos tres tipos aparecen en la lista normal de contenido y se pueden abrir/editar desde el editor de código.

## Pixel Studio mejorado

- Nuevo botón **Importar PNG**.
- Acepta PNG externos y los lleva al lienzo Pixel Studio.
- Conserva tamaños 16×16, 32×32, 64×64 y 128×128; otras dimensiones se ajustan a un lienzo compatible sin suavizado para mantener el aspecto pixel-art.
- El nombre del archivo importado se usa como nombre inicial de textura.

## Modelador

- **Duplicar cubo** crea una copia editable del cubo seleccionado.
- **Espejo X** refleja el cubo alrededor del eje X del modelo.
- Se mantienen jerarquías de huesos, pivotes, Box UV, UV por cara, Auto UV, vista previa 3D y timeline por huesos.

## Corrección importante

La v0.5 tenía un error en el generador de UUID: cuando `crypto.randomUUID()` estaba disponible intentaba llamar accidentalmente a una función global inexistente. En v0.6 usa correctamente `crypto.randomUUID()` y conserva el fallback basado en `getRandomValues()`.

## Tipos de proyecto

- **Mod / Add-On completo** — BP + RP, exportación `.mcaddon`.
- **Una sola cosa** — objeto, bloque, entidad, receta, loot table, spawn rule o script en un Add-On mínimo.
- **Pack de texturas** — solo RP, exportación `.mcpack`.
- **Una textura** — Pixel Studio independiente, exportación `.png`.
- **Modelo + animación** — geometría/UV/timeline, exportación `.geo.json` o RP.

## Funciones principales heredadas

- Objetos, bloques, entidades y JavaScript Script API.
- Editor JSON/JavaScript.
- Behavior Pack + Resource Pack.
- Modelador con varios huesos/cubos y jerarquía padre/hijo.
- Timeline con múltiples animaciones y keyframes `rotation`, `position` y `scale`.
- Pixel Studio con lápiz, goma, relleno, simetría X, undo/redo y PNG.
- Importación `.geo.json` y envío de geometría a Blockbench Web.
- Inspector de manifests, UUID, JSON, texturas, geometrías, animaciones y ahora contenido del Creator Lab.
- Exportación `.mcpack`, `.mcaddon`, `.png`, `.geo.json` y proyecto `.bmsproject.json`.
- PWA/offline con cache `bms-v0.6`.

## Ejecutar

Sirve la carpeta mediante HTTP local, por ejemplo:

```bash
python -m http.server 8080
```

Luego abre `http://localhost:8080`.

## Notas de formato Bedrock

- Las recetas shaped/shapeless se generan con `format_version: 1.20.10`, `tags: ["crafting_table"]`, `result` y los campos `pattern`/`key` o `ingredients` según corresponda.
- Las loot tables se guardan bajo `loot_tables` y usan `pools`, `rolls` y entradas ponderadas.
- Las spawn rules se generan con `format_version: 1.8.0`, `minecraft:spawn_rules`, `description`, `conditions`, `minecraft:weight` y `minecraft:herd`.
- Los manifests BP/RP siguen usando `format_version: 2`.

## Estado

v0.6 Alpha. Los nuevos generadores fueron probados con datos de ejemplo y sus JSON se analizaron correctamente. El arranque también se probó mediante un DOM simulado porque Chromium headless no termina de iniciar de forma fiable en este contenedor. Falta seguir ampliando filtros de spawn/biomas, funciones avanzadas de loot, tags de recetas, gizmos 3D directos, animation controllers visuales y herramientas de sonido/partículas.
