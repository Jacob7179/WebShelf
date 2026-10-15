(function () {
  if (window.__webShelfExportInstalled) return;
  if (typeof window.WebShelfExport === 'undefined') return;
  window.__webShelfExportInstalled = true;
  var waiting = new Map();
  window.__wsExportReady = function (id, ok) {
    var resolve = waiting.get(id);
    if (resolve) { waiting.delete(id); resolve(!!ok); }
  };
  async function saveBlob(blob, name) {
    var id = 'export-' + Date.now() + '-' + Math.random().toString(36).slice(2);
    var ready = await new Promise(function (resolve) {
      waiting.set(id, resolve);
      try { WebShelfExport.choose(id, name || 'download', blob.type || 'application/octet-stream'); }
      catch (error) { waiting.delete(id); resolve(false); }
    });
    if (!ready) return;
    var failed = false;
    try {
      for (var offset = 0; offset < blob.size; offset += 32768) {
        var bytes = new Uint8Array(await blob.slice(offset, offset + 32768).arrayBuffer());
        var binary = '';
        for (var i = 0; i < bytes.length; ++i) binary += String.fromCharCode(bytes[i]);
        if (!WebShelfExport.chunk(id, btoa(binary))) throw new Error('Unable to save file');
      }
    } catch (error) { failed = true; console.error('WebShelf export failed', error); }
    finally { WebShelfExport.end(id); }
    if (failed) console.warn('WebShelf export may be incomplete');
  }
  function handle(anchor) {
    if (!anchor || !anchor.hasAttribute || !anchor.hasAttribute('download')) return false;
    var url = anchor.href || '';
    if (!/^(blob:|data:)/i.test(url)) return false;
    // Fetch immediately. Some websites revoke blob: URLs immediately after click.
    try {
      var content = fetch(url).then(function (response) {
        if (!response.ok) throw new Error('Could not read export');
        return response.blob();
      });
      content.then(function(blob) { return saveBlob(blob, anchor.getAttribute('download') || 'download'); })
        .catch(function(error) { console.error('WebShelf export:', error); });
    } catch (error) { console.error('WebShelf export:', error); }
    return true;
  }
  // Programmatic a.click() is used by YouTube Music Player and many export libraries.
  // Intercept it before the WebView attempts an unsupported blob: download.
  var originalClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (handle(this)) return;
    return originalClick.apply(this, arguments);
  };
  // Support actual user taps and dispatchEvent-based clicks too.
  document.addEventListener('click', function (event) {
    var target = event.target;
    var anchor = target && target.closest && target.closest('a[download]');
    if (handle(anchor)) { event.preventDefault(); event.stopImmediatePropagation(); }
  }, true);
})();
