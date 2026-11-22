/* Ditherer Android: save generated downloads into MediaStore via small base64 chunks.
 * The original desktop app remains unchanged. Files stay local on the device. */
(() => {
  const native = window.DithererAndroid;
  if (!native) return;

  const allowed = /^(blob:|data:)/i;
  const inferMime = (name) => {
    const ext = (name || '').split('.').pop().toLowerCase();
    return ({png:'image/png',jpg:'image/jpeg',jpeg:'image/jpeg',webp:'image/webp',gif:'image/gif',
      mp4:'video/mp4',webm:'video/webm',json:'application/json',zip:'application/zip',
      txt:'text/plain'})[ext] || 'application/octet-stream';
  };

  async function save(href, name) {
    let id = '';
    try {
      const response = await fetch(href);
      if (!response.ok) throw new Error('Cannot read export');
      const blob = await response.blob();
      const mime = blob.type || inferMime(name);
      id = native.begin(name || 'ditherer-export', mime);
      if (!id) throw new Error('Cannot open Android Downloads');
      const stream = blob.stream().getReader();
      while (true) {
        const {done, value} = await stream.read();
        if (done) break;
        // At most 48 KiB per JNI call. Avoid JS argument/stack and bridge limits.
        for (let start = 0; start < value.length; start += 48 * 1024) {
          const slice = value.subarray(start, start + 48 * 1024);
          let binary = '';
          for (let i = 0; i < slice.length; i += 8192)
            binary += String.fromCharCode(...slice.subarray(i, i + 8192));
          if (!native.chunk(id, btoa(binary))) throw new Error('File write failed');
        }
      }
      if (!native.finish(id)) throw new Error('File finalization failed');
    } catch (error) {
      if (id) native.abort(id);
      console.error('Ditherer Android export failed', error);
      alert('Не удалось сохранить файл: ' + (error?.message || error));
    }
  }

  document.addEventListener('click', (event) => {
    const anchor = event.target?.closest?.('a[download]');
    if (!anchor || !allowed.test(anchor.href)) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    void save(anchor.href, anchor.getAttribute('download'));
  }, true);
})();
