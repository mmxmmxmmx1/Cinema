(function () {
  'use strict';

  const scriptPlan = [
    {
      globalName: 'Vue',
      primary: 'https://unpkg.com/vue@3.3.4/dist/vue.global.prod.js',
      fallback: 'https://cdn.jsdelivr.net/npm/vue@3.3.4/dist/vue.global.prod.js'
    },
    {
      globalName: 'VueRouter',
      primary: 'https://unpkg.com/vue-router@4.2.4/dist/vue-router.global.prod.js',
      fallback: 'https://cdn.jsdelivr.net/npm/vue-router@4.2.4/dist/vue-router.global.prod.js'
    }
  ];

  const appScriptPlan = [
    {
      key: 'CinemaSpaShared',
      url: '/js/modules/spa-shared.js?v=20260219-01',
      loaded: function () { return !!window.CinemaSpaShared; }
    },
    {
      key: 'CinemaAppPages',
      url: '/js/modules/app-pages.js?v=20260219-01',
      loaded: function () { return !!window.CinemaAppPages; }
    },
    {
      key: '__cinemaSpaBooted',
      url: '/js/app.js?v=20260219-02',
      loaded: function () { return !!window.__cinemaSpaBooted; }
    }
  ];

  function hasScriptTag(url) {
    const scripts = document.querySelectorAll('script[src]');
    return Array.from(scripts).some(function (s) {
      return s.getAttribute('src') === url;
    });
  }

  function loadScript(url) {
    return new Promise(function (resolve, reject) {
      if (hasScriptTag(url)) {
        resolve();
        return;
      }
      const script = document.createElement('script');
      script.src = url;
      script.defer = true;
      script.onload = function () {
        resolve();
      };
      script.onerror = function () {
        reject(new Error('Failed to load script: ' + url));
      };
      document.head.appendChild(script);
    });
  }

  async function ensureVendorLoaded(entry) {
    if (window[entry.globalName]) {
      return;
    }
    try {
      await loadScript(entry.primary);
    } catch (_) {
      await loadScript(entry.fallback);
    }
    if (!window[entry.globalName]) {
      throw new Error(entry.globalName + ' did not become available after loading.');
    }
  }

  async function ensureAppScriptLoaded(entry) {
    if (entry.loaded()) {
      return;
    }
    await loadScript(entry.url);
    if (!entry.loaded()) {
      throw new Error(entry.key + ' did not become available after loading.');
    }
  }

  function showBootError(message) {
    const root = document.getElementById('root');
    if (!root) {
      return;
    }
    root.classList.add('show');
    root.innerHTML = '<div style="padding:48px 24px;color:#ffb4a8;font:600 20px/1.6 Noto Sans TC,sans-serif;text-align:center;">前端載入失敗：' +
      String(message || '未知錯誤') + '<br/>請按 Ctrl+F5 強制重新整理後再試。</div>';
  }

  async function boot() {
    try {
      for (const entry of scriptPlan) {
        await ensureVendorLoaded(entry);
      }
      window.dispatchEvent(new Event('cinema:vendor-ready'));
      for (const entry of appScriptPlan) {
        await ensureAppScriptLoaded(entry);
      }
    } catch (err) {
      const message = err && err.message ? err.message : 'vendor-load-failed';
      showBootError(message);
      window.dispatchEvent(new CustomEvent('cinema:vendor-failed', {
        detail: { message }
      }));
    }
  }

  boot();
})();
