/* Android-only download bridge. The original shaders and export code are unchanged. */
(() => {
  'use strict';
  if (!window.FormAndroid) return;

  // Programmatic blob-anchor downloads are unreliable in Android WebView.
  // Intercept only the exact mechanism used by upstream main.js.
  const originalClick = HTMLAnchorElement.prototype.click;
  let saving = false;

  async function saveBlobUrl(url, filename) {
    if (saving) {
      document.getElementById('status').textContent = 'Previous save is still running';
      return;
    }
    saving = true;
    let id = null;
    try {
      const response = await fetch(url);
      if (!response.ok) throw new Error('Cannot read the exported file');
      const blob = await response.blob();
      const ext = filename.toLowerCase().endsWith('.png') ? 'image/png' : 'video/webm';
      id = window.FormAndroid.begin(filename, ext);
      if (!id) throw new Error('Android could not create a Downloads file');
      const chunkSize = 64 * 1024;
      const count = Math.ceil(blob.size / chunkSize);
      for (let i = 0; i < count; i++) {
        const bytes = new Uint8Array(await blob.slice(i * chunkSize, (i + 1) * chunkSize).arrayBuffer());
        let binary = '';
        for (let j = 0; j < bytes.length; j += 4096) {
          binary += String.fromCharCode.apply(null, bytes.subarray(j, j + 4096));
        }
        if (!window.FormAndroid.chunk(id, btoa(binary))) throw new Error('Failed writing file');
        if (i % 8 === 0) await new Promise(resolve => setTimeout(resolve, 0));
      }
      if (!window.FormAndroid.finish(id)) throw new Error('Failed to finalize the file');
      id = null;
      document.getElementById('status').textContent = 'Saved to Downloads/Form';
    } catch (error) {
      if (id) window.FormAndroid.cancel(id);
      document.getElementById('status').textContent = error?.message || 'Save failed';
      console.error('Form Mobile export failed:', error);
    } finally {
      saving = false;
    }
  }

  HTMLAnchorElement.prototype.click = function (...args) {
    if (this.download && this.href && this.href.startsWith('blob:')) {
      void saveBlobUrl(this.href, this.download);
      return;
    }
    return originalClick.apply(this, args);
  };

  // A practical lower-memory option for mobile GPUs. 2K and 4K remain available.
  const quality = document.getElementById('quality');
  if (quality && !quality.querySelector('[value="1024"]')) {
    const option = document.createElement('option');
    option.value = '1024';
    option.textContent = '1K (mobile)';
    quality.insertBefore(option, quality.firstChild);
    quality.value = '1024';
    quality.dispatchEvent(new Event('change', { bubbles: true }));
  }
})();
