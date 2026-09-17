(function(root) {
  const api = {
    escape(value) { return String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); },
    parse(text) {
      return [...new Set((text.toUpperCase().match(/\b[A-Z]{1,3}-\d{2,4}-\d{1,3}\b/g) || []))]
        .map(shelf => ({name:'未提供商品名', shelf}));
    }
  };
  if (typeof module !== 'undefined') module.exports = api;
  else root.ImportUtils = api;
})(globalThis);
