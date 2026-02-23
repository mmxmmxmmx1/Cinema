(function () {
  'use strict';

  function normalizePath(inputPath) {
    if (!inputPath || typeof inputPath !== 'string') {
      return '/';
    }
    let pathname = inputPath.split('?')[0].split('#')[0];
    if (!pathname.startsWith('/')) {
      pathname = '/' + pathname;
    }
    if (pathname.length > 1 && pathname.endsWith('/')) {
      pathname = pathname.slice(0, -1);
    }
    return pathname || '/';
  }

  function compilePath(pattern) {
    const keys = [];
    const escaped = pattern
      .replace(/([.+?^${}()|[\]\\])/g, '\\$1')
      .replace(/:(\w+)/g, function (_, key) {
        keys.push(key);
        return '([^/]+)';
      });
    return {
      pattern,
      keys,
      regex: new RegExp('^' + escaped + '$')
    };
  }

  function buildSpaRouter(vueApi, pages) {
    const { reactive, h } = vueApi;
    const routeTable = [
      { path: '/', component: pages.HomePage },
      { path: '/movies/:movieId', component: pages.MovieDetailPage },
      { path: '/movies/:movieId/showtimes/:showtimeId', component: pages.SeatSelectionPage },
      { path: '/checkout/:movieId/showtimes/:showtimeId', component: pages.CheckoutPage },
      { path: '/orders', component: pages.OrdersPage },
      { path: '/orders/:orderId', component: pages.OrderDetailPage }
    ].map(function (route) {
      return { ...route, compiled: compilePath(route.path) };
    });

    function resolve(pathname) {
      const normalized = normalizePath(pathname);
      for (const route of routeTable) {
        const matched = route.compiled.regex.exec(normalized);
        if (!matched) {
          continue;
        }
        const params = {};
        route.compiled.keys.forEach(function (key, index) {
          params[key] = decodeURIComponent(matched[index + 1] || '');
        });
        return { matched: true, path: normalized, params, component: route.component };
      }
      return { matched: false, path: '/', params: {}, component: pages.HomePage };
    }

    const routeState = reactive({
      path: '/',
      params: {},
      fullPath: '/',
      component: pages.HomePage
    });

    function syncFromLocation(rewriteUnknown) {
      const current = resolve(window.location.pathname);
      if (!current.matched && rewriteUnknown) {
        window.history.replaceState({}, '', '/');
      }
      routeState.path = current.path;
      routeState.params = current.params;
      routeState.component = current.component;
      routeState.fullPath = window.location.pathname + window.location.search + window.location.hash;
    }

    function navigate(to, replace) {
      const target = normalizePath(typeof to === 'string' ? to : (to && to.path) || '/');
      const resolved = resolve(target);
      const finalPath = resolved.matched ? target : '/';
      if (replace) {
        window.history.replaceState({}, '', finalPath);
      } else if (window.location.pathname !== finalPath) {
        window.history.pushState({}, '', finalPath);
      }
      syncFromLocation(false);
    }

    const routerApi = {
      push: function (to) { navigate(to, false); },
      replace: function (to) { navigate(to, true); },
      go: function (delta) { window.history.go(typeof delta === 'number' ? delta : -1); }
    };

    const RouterView = {
      name: 'RouterView',
      setup: function () {
        return function () {
          return h(routeState.component);
        };
      }
    };

    const RouterLink = {
      name: 'RouterLink',
      inheritAttrs: false,
      props: {
        to: {
          type: [String, Object],
          required: true
        }
      },
      setup: function (props, context) {
        function toPath() {
          if (typeof props.to === 'string') {
            return normalizePath(props.to);
          }
          if (props.to && typeof props.to.path === 'string') {
            return normalizePath(props.to.path);
          }
          return '/';
        }

        function onClick(event) {
          if (!event || event.defaultPrevented) {
            return;
          }
          if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return;
          }
          event.preventDefault();
          routerApi.push(toPath());
        }

        return function () {
          const attrs = context.attrs || {};
          return h('a', {
            ...attrs,
            href: toPath(),
            onClick
          }, context.slots.default ? context.slots.default() : toPath());
        };
      }
    };

    window.addEventListener('popstate', function () {
      syncFromLocation(false);
    });

    syncFromLocation(true);

    return { routeState, routerApi, RouterView, RouterLink };
  }

  function bootstrapSpa() {
    if (window.__cinemaSpaBooted) {
      return;
    }
    if (!window.Vue) {
      return;
    }
    const shared = window.CinemaSpaShared;
    const pages = window.CinemaAppPages;
    if (!shared || !pages) {
      throw new Error('Cinema SPA modules are not loaded.');
    }

    const { createApp } = window.Vue;
    const {
      BookingWindowBanner,
      GlobalAuthWidget
    } = shared;
    const {
      HomePage,
      MovieDetailPage,
      SeatSelectionPage,
      CheckoutPage,
      OrdersPage,
      OrderDetailPage
    } = pages;

    const router = buildSpaRouter(window.Vue, {
      HomePage,
      MovieDetailPage,
      SeatSelectionPage,
      CheckoutPage,
      OrdersPage,
      OrderDetailPage
    });

    const RootApp = {
      template: `
        <a class="skip-link" href="#spa-content">跳到主要內容</a>
        <div class="root-shell">
          <GlobalAuthWidget />
          <BookingWindowBanner />
          <main id="spa-content" tabindex="-1">
            <router-view></router-view>
          </main>
        </div>
      `,
      components: { GlobalAuthWidget, BookingWindowBanner }
    };

    const app = createApp(RootApp);
    app.component('router-view', router.RouterView);
    app.component('router-link', router.RouterLink);
    app.config.globalProperties.$router = router.routerApi;
    app.config.globalProperties.$route = router.routeState;
    app.mount('#root');
    window.__cinemaSpaBooted = true;
  }

  function handleVendorFailure(evt) {
    const detail = evt && evt.detail ? evt.detail : {};
    const message = detail.message || 'Unknown vendor loading error';
    throw new Error('Front-end vendor loading failed: ' + message);
  }

  window.addEventListener('cinema:vendor-ready', bootstrapSpa);
  window.addEventListener('cinema:vendor-failed', handleVendorFailure);

  // In case vendors are already loaded (cached, or this script executes after loader).
  bootstrapSpa();
})();
