# Android static checks

Generated: 2026-10-07

- [x] Bridge JS→Java completo — 11 métodos enlazados
- [x] Sin String.isBlank() — Compatibilidad API 24
- [x] Portrait declarado
- [x] Teclado adjustResize
- [x] Minecraft visible en package queries
- [x] minSdk 24
- [x] targetSdk 36
- [x] WebView bloquea red embebida
- [x] Autosave begin/end
- [x] Imports nativos
- [x] Back inteligente
- [x] Edge-to-edge insets

## AndroidBridge methods referenced by JavaScript hook

- appendAutosaveChunk
- appendFileChunk
- beginAutosave
- beginFile
- cancelFile
- finishAutosave
- finishFile
- getAutosaveBase64
- hasAutosave
- notifyError
- openExternal

## Result

PASS: all static integration checks passed.

These checks do not replace an Android build, emulator test, or physical-device test.
