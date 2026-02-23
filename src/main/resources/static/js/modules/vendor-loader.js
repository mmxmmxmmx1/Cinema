(function () {
  'use strict';

  let lastRuntimeErrorMessage = null;

  function rememberRuntimeError(value) {
    if (!value) {
      return;
    }
    const text = String(value);
    if (!text || text === 'undefined') {
      return;
    }
    lastRuntimeErrorMessage = text;
  }

  window.addEventListener('error', function (event) {
    if (!event) {
      return;
    }
    rememberRuntimeError((event.error && (event.error.stack || event.error.message)) || event.message);
  }, true);

  window.addEventListener('unhandledrejection', function (event) {
    if (!event) {
      return;
    }
    const reason = event.reason;
    if (reason && typeof reason === 'object') {
      rememberRuntimeError(reason.stack || reason.message || JSON.stringify(reason));
      return;
    }
    rememberRuntimeError(reason);
  });

  const scriptPlan = [
    {
      globalName: 'Vue',
      primary: '/js/vendor/vue.global.prod.js?v=20260223-01',
      fallback: '/js/vendor/vue.global.js?v=20260223-01'
    }
  ];

  const appScriptPlan = [
    {
      key: 'CinemaSpaShared',
      url: '/js/modules/spa-shared.js?v=20260223-01',
      loaded: function () { return !!window.CinemaSpaShared; }
    },
    {
      key: 'CinemaAppPages',
      url: '/js/modules/app-pages.js?v=20260223-01',
      loaded: function () { return !!window.CinemaAppPages; }
    },
    {
      key: '__cinemaSpaBooted',
      url: '/js/app.js?v=20260223-01',
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
      const runtimeSuffix = lastRuntimeErrorMessage
        ? ' runtime=' + lastRuntimeErrorMessage
        : '';
      throw new Error(entry.key + ' did not become available after loading.' + runtimeSuffix);
    }
  }

  function showBootError(message) {
    const root = document.getElementById('root');
    if (!root) {
      return;
    }
    root.classList.add('show');
    root.innerHTML = '<div style="padding:48px 24px;color:#ffb4a8;font:600 20px/1.6 Noto Sans TC,sans-serif;text-align:center;">前端載入失敗：' +
      String(message || '未知錯誤') + '<br/>請按 Ctrl+F5 強制重新整理後再試；若仍失敗，請確認 /js/vendor/* 檔案可正常讀取。</div>';
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
