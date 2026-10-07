(async () => {
  const PARTS = Array.from({ length: 7 }, (_, i) => `./bundle/${String(i).padStart(2, '0')}.b64`);

  function showFatal(error) {
    console.error('Bedrock Mod Studio no pudo iniciar:', error);
    const box = document.createElement('pre');
    box.style.cssText = 'white-space:pre-wrap;padding:16px;margin:16px;background:#29151a;color:#ffd7dc;border:1px solid #7d3642;border-radius:10px;font:13px/1.5 ui-monospace,monospace';
    box.textContent = 'Error al iniciar Bedrock Mod Studio:\n' + (error?.stack || error);
    document.body.appendChild(box);
  }

  try {
    if (!('DecompressionStream' in globalThis)) {
      throw new Error('Este navegador no soporta DecompressionStream(gzip). Usa una versión moderna de Chrome, Edge, Firefox o Safari.');
    }

    const responses = await Promise.all(PARTS.map(async (url) => {
      const response = await fetch(url);
      if (!response.ok) throw new Error(`No se pudo cargar ${url} (HTTP ${response.status})`);
      return (await response.text()).trim();
    }));

    const binary = atob(responses.join(''));
    const compressed = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) compressed[i] = binary.charCodeAt(i);

    const stream = new Blob([compressed]).stream().pipeThrough(new DecompressionStream('gzip'));
    const source = await new Response(stream).text();

    const script = document.createElement('script');
    script.dataset.bmsBundle = 'v0.6';
    script.textContent = source;
    document.head.appendChild(script);
  } catch (error) {
    showFatal(error);
  }
})();
