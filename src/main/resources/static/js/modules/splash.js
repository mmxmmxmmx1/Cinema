(function () {
  document.addEventListener('DOMContentLoaded', function () {
    const splashScreen = document.getElementById('splash-screen');
    const appContent = document.getElementById('root');

    if (!splashScreen || !appContent) {
      return;
    }

    const prefersReducedMotion = window.matchMedia
      ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
      : false;

    const totalSplashMs = prefersReducedMotion ? 350 : 1800;
    const splashFadeMs = prefersReducedMotion ? 0 : 500;
    const splashVisibleMs = Math.max(0, totalSplashMs - splashFadeMs);

    setTimeout(function () {
      if (splashFadeMs > 0) {
        splashScreen.classList.add('fade-out');
      }

      setTimeout(function () {
        splashScreen.style.display = 'none';
        appContent.classList.add('show');
      }, splashFadeMs);
    }, splashVisibleMs);
  });
})();
