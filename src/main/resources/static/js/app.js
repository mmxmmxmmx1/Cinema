(function () {
  'use strict';

  function bootstrapSpa() {
    if (window.__cinemaSpaBooted) {
      return;
    }
    if (!window.Vue || !window.VueRouter) {
      return;
    }
    const shared = window.CinemaSpaShared;
    const pages = window.CinemaAppPages;
    if (!shared || !pages) {
      throw new Error('Cinema SPA modules are not loaded.');
    }

    const { createApp } = window.Vue;
    const { createRouter, createWebHistory } = window.VueRouter;
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

    const routes = [
      { path: '/', component: HomePage },
      { path: '/movies/:movieId', component: MovieDetailPage },
      { path: '/movies/:movieId/showtimes/:showtimeId', component: SeatSelectionPage },
      { path: '/checkout/:movieId/showtimes/:showtimeId', component: CheckoutPage },
      { path: '/orders', component: OrdersPage },
      { path: '/orders/:orderId', component: OrderDetailPage },
      { path: '/:pathMatch(.*)*', redirect: '/' }
    ];

    const router = createRouter({ history: createWebHistory(), routes });
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
    app.use(router);
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
