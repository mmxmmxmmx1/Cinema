(function (window) {
  const LoadingState = {
    template: '<div class="loading">Loading…</div>'
  };

  const ErrorState = {
    template: '<div class="error-state">{{ message }}</div>',
    props: ['message']
  };

  // Global auth state for the SPA (member vs employee vs anonymous).
  const authStore = Vue.reactive({
    loaded: false,
    authenticated: false, // backward-compat: "is MEMBER"
    username: null,
    member: false,
    employee: false,
    roles: []
  });

  async function refreshMemberAuth() {
    try {
      const res = await fetch('/api/auth/member', { credentials: 'same-origin' });
      const json = res.ok ? await res.json() : null;
      authStore.loaded = true;
      authStore.authenticated = !!(json && json.authenticated);
      authStore.username = json && typeof json.username === 'string' ? json.username : null;
      authStore.member = !!(json && json.member);
      authStore.employee = !!(json && json.employee);
      authStore.roles = Array.isArray(json && json.roles) ? json.roles : [];
      return json;
    } catch (_) {
      authStore.loaded = true;
      authStore.authenticated = false;
      authStore.username = null;
      authStore.member = false;
      authStore.employee = false;
      authStore.roles = [];
      return null;
    }
  }

  function currentSpaPath() {
    return window.location.pathname + window.location.search + window.location.hash;
  }

  const bookingWindowStore = Vue.reactive({
    loaded: false,
    bookingOpen: true,
    warning: false,
    now: null,
    openTime: '07:00',
    closeTime: '22:45',
    message: ''
  });

  async function refreshBookingWindow() {
    try {
      const res = await fetch('/api/movies/booking-window', { credentials: 'same-origin' });
      if (!res.ok) throw new Error('booking-window-api-failed');
      const json = await res.json();
      bookingWindowStore.loaded = true;
      bookingWindowStore.bookingOpen = !!(json && json.bookingOpen);
      bookingWindowStore.warning = !!(json && json.warning);
      bookingWindowStore.now = json && typeof json.now === 'string' ? json.now : null;
      bookingWindowStore.openTime = json && typeof json.openTime === 'string' ? json.openTime : '07:00';
      bookingWindowStore.closeTime = json && typeof json.closeTime === 'string' ? json.closeTime : '22:45';
      bookingWindowStore.message = json && typeof json.message === 'string' ? json.message : '';
      return json;
    } catch (_) {
      if (!bookingWindowStore.loaded) {
        bookingWindowStore.loaded = true;
        bookingWindowStore.bookingOpen = true;
        bookingWindowStore.warning = false;
        bookingWindowStore.message = '';
      }
      return null;
    }
  }

  const BookingWindowBanner = {
    data() {
      return { timerId: null };
    },
    template: `
      <div v-if="visible" class="booking-banner" :class="bannerClass">
        <strong class="booking-banner-title">{{ bannerTitle }}</strong>
        <span>{{ bannerMessage }}</span>
      </div>
    `,
    computed: {
      window() { return bookingWindowStore; },
      visible() {
        if (!this.window.loaded) return false;
        return this.window.warning || !this.window.bookingOpen;
      },
      bannerClass() {
        return this.window.warning ? 'warning' : 'closed';
      },
      bannerTitle() {
        return this.window.warning ? '訂票提醒：' : '訂票狀態：';
      },
      bannerMessage() {
        if (this.window.message) return this.window.message;
        if (this.window.warning) {
          return `22:45 將強制停止訂票，請儘速完成付款。`;
        }
        return `目前非訂票時段（每日 ${this.window.openTime} - ${this.window.closeTime}）。`;
      }
    },
    async mounted() {
      await refreshBookingWindow();
      this.timerId = setInterval(() => {
        refreshBookingWindow();
      }, 30000);
    },
    unmounted() {
      if (this.timerId) {
        clearInterval(this.timerId);
        this.timerId = null;
      }
    }
  };

  const GlobalAuthWidget = {
    data() {
      return { actionError: null };
    },
    template: `
      <div class="floating-buttons">
        <a class="floating-button login" :href="memberEntryHref">{{ memberEntryLabel }}</a>
        <button v-if="auth.member" type="button" class="floating-button logout" @click="logoutMember">會員登出</button>
        <a class="floating-button employee" :href="employeeEntryHref">{{ employeeEntryLabel }}</a>
        <span v-if="actionError" class="auth-hint" style="color:#ff9a9a;">{{ actionError }}</span>
      </div>
    `,
    computed: {
      auth() { return authStore; },
      defaultReturnTo() {
        // If the user is currently in a checkout-related flow, return to the same SPA route.
        // Otherwise, default to the server-rendered member page so users see "member-only" content after login.
        const path = window.location && window.location.pathname ? window.location.pathname : '/';
        if (path.startsWith('/checkout/') || path.startsWith('/movies/')) {
          return currentSpaPath();
        }
        if (path.startsWith('/orders')) {
          return currentSpaPath();
        }
        if (path.startsWith('/member/orders')) {
          return '/member/orders';
        }
        return '/member';
      },
      memberLoginHref() {
        return `/member/login?returnTo=${encodeURIComponent(this.defaultReturnTo)}`;
      },
      memberEntryHref() {
        return this.auth.member ? '/member' : this.memberLoginHref;
      },
      memberEntryLabel() {
        if (!this.auth.loaded) return '會員';
        return this.auth.member ? `會員專區：${this.auth.username || '已登入'}` : '會員登入';
      },
      employeeEntryHref() {
        return this.auth.employee ? '/employee' : '/employee/login';
      },
      employeeEntryLabel() {
        if (!this.auth.loaded) return '員工專區';
        return this.auth.employee ? `員工專區：${this.auth.username || '已登入'}` : '員工專區';
      }
    },
    methods: {
      getCookie(name) {
        const target = name + '=';
        const parts = document.cookie ? document.cookie.split(';') : [];
        for (let i = 0; i < parts.length; i++) {
          const part = parts[i].trim();
          if (part.startsWith(target)) return decodeURIComponent(part.substring(target.length));
        }
        return null;
      },
      async ensureCsrfCookie() {
        try {
          await fetch('/api/csrf', { credentials: 'same-origin' });
        } catch (_) {}
      },
      async logoutMember() {
        this.actionError = null;
        try {
          await this.ensureCsrfCookie();
          const xsrf = this.getCookie('XSRF-TOKEN');
          const res = await fetch('/member/logout', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { ...(xsrf ? { 'X-XSRF-TOKEN': xsrf } : {}) }
          });
          if (!res.ok && res.status !== 302) {
            this.actionError = `登出失敗（${res.status}）`;
            return;
          }
        } catch (_) {
          this.actionError = '登出失敗，請稍後再試。';
          return;
        }
        await refreshMemberAuth();
        window.location.href = '/';
      }
    },
    async mounted() {
      await refreshMemberAuth();
    }
  };

  const Hero = {
    template: `
      <header class="hero">
        <div class="hero-inner">
          <span class="hero-label">現正熱映中</span>
          <img class="hero-badge" src="/images/sleep.jpg" alt="Very Sleepy Cinema 徽章">
          <div class="hero-text">
            <h1>很好睡電影院</h1>
            <p style="color: #0044BB;">每個顧客都可以睡得很安穩</p>
          </div>
        </div>
      </header>
    `
  };

  window.CinemaSpaShared = Object.freeze({
    LoadingState,
    ErrorState,
    authStore,
    refreshMemberAuth,
    currentSpaPath,
    bookingWindowStore,
    refreshBookingWindow,
    BookingWindowBanner,
    GlobalAuthWidget,
    Hero
  });
})(window);
