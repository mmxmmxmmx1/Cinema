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
      return {
        actionError: null,
        memberMenuOpen: false,
        employeeMenuOpen: false,
        showRegisterModal: false,
        registerSubmitting: false,
        registerError: null,
        registerSuccess: null,
        registerForm: {
          nickname: '',
          password: '',
          confirmPassword: ''
        },
        onDocumentClick: null,
        onEscapeKey: null
      };
    },
    template: `
      <div>
        <div class="floating-buttons">
          <div class="floating-dropdown">
            <button
              type="button"
              class="floating-button menu-trigger"
              :aria-expanded="memberMenuOpen ? 'true' : 'false'"
              aria-label="會員專區選單"
              @click.stop="toggleMemberMenu"
            >
              {{ memberTriggerLabel }}
            </button>
            <div v-if="memberMenuOpen" class="floating-menu">
              <a class="floating-menu-item" :href="memberEntryHref">{{ memberMenuLinkLabel }}</a>
              <button
                v-if="!auth.member"
                type="button"
                class="floating-menu-item"
                @click="openRegisterModal"
              >
                申請會員
              </button>
              <button
                v-if="auth.member"
                type="button"
                class="floating-menu-item"
                @click="logoutMember"
              >
                登出會員
              </button>
            </div>
          </div>

          <div class="floating-dropdown">
            <button
              type="button"
              class="floating-button menu-trigger"
              :aria-expanded="employeeMenuOpen ? 'true' : 'false'"
              aria-label="員工專區選單"
              @click.stop="toggleEmployeeMenu"
            >
              {{ employeeTriggerLabel }}
            </button>
            <div v-if="employeeMenuOpen" class="floating-menu">
              <a class="floating-menu-item" :href="employeeEntryHref">{{ employeeMenuLinkLabel }}</a>
              <button
                v-if="auth.employee"
                type="button"
                class="floating-menu-item"
                @click="logoutEmployee"
              >
                登出員工
              </button>
            </div>
          </div>

          <span v-if="actionError" class="auth-hint auth-hint-error">{{ actionError }}</span>
        </div>

        <div v-if="showRegisterModal" class="auth-modal-mask">
          <div class="auth-modal" role="dialog" aria-modal="true" aria-label="加入會員">
            <div class="auth-modal-head">
              <h3>加入會員</h3>
              <button type="button" class="auth-modal-close" @click="closeRegisterModal" aria-label="關閉">×</button>
            </div>
            <p class="auth-modal-subtitle">註冊後可使用會員專區、訂單紀錄與點數功能。</p>

            <form class="auth-register-form" @submit.prevent="submitRegister">
              <label>
                帳號
                <input v-model.trim="registerForm.nickname" type="text" maxlength="100" pattern="[A-Za-z0-9]+" autocomplete="username" placeholder="例如：newmember" required>
                <small class="auth-field-help">僅限英文與數字（不可使用中文或符號）</small>
              </label>
              <label>
                密碼
                <input v-model="registerForm.password" type="password" maxlength="100" autocomplete="new-password" placeholder="請輸入密碼" required>
              </label>
              <label>
                確認密碼
                <input v-model="registerForm.confirmPassword" type="password" maxlength="100" autocomplete="new-password" placeholder="再次輸入密碼" required>
              </label>

              <div v-if="registerError" class="auth-modal-message error">{{ registerError }}</div>
              <div v-if="registerSuccess" class="auth-modal-message success">{{ registerSuccess }}</div>

              <div class="auth-register-actions">
                <button type="submit" class="floating-button register submit" :disabled="registerSubmitting">
                  {{ registerSubmitting ? '註冊中…' : '建立會員' }}
                </button>
                <a class="floating-button login" :href="memberLoginHref">前往會員登入</a>
              </div>
            </form>
          </div>
        </div>
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
      memberTriggerLabel() {
        if (!this.auth.loaded) return '會員專區';
        return this.auth.member ? `會員專區（${this.auth.username || '已登入'}）` : '會員專區';
      },
      memberMenuLinkLabel() {
        if (!this.auth.loaded) return '會員登入';
        return this.auth.member ? '前往會員專區' : '會員登入';
      },
      employeeEntryHref() {
        return this.auth.employee ? '/employee' : '/employee/login';
      },
      employeeTriggerLabel() {
        if (!this.auth.loaded) return '員工專區';
        return this.auth.employee ? `員工專區（${this.auth.username || '已登入'}）` : '員工專區';
      },
      employeeMenuLinkLabel() {
        if (!this.auth.loaded) return '員工登入';
        return this.auth.employee ? '前往員工專區' : '員工登入';
      }
    },
    methods: {
      closeMenus() {
        this.memberMenuOpen = false;
        this.employeeMenuOpen = false;
      },
      toggleMemberMenu() {
        this.memberMenuOpen = !this.memberMenuOpen;
        if (this.memberMenuOpen) {
          this.employeeMenuOpen = false;
        }
      },
      toggleEmployeeMenu() {
        this.employeeMenuOpen = !this.employeeMenuOpen;
        if (this.employeeMenuOpen) {
          this.memberMenuOpen = false;
        }
      },
      handleOutsideClick(event) {
        if (!this.$el) return;
        if (!this.$el.contains(event.target)) {
          this.closeMenus();
        }
      },
      handleEscape(event) {
        if (event && event.key === 'Escape') {
          this.closeMenus();
          if (this.showRegisterModal) {
            this.closeRegisterModal();
          }
        }
      },
      openRegisterModal() {
        this.closeMenus();
        this.showRegisterModal = true;
        this.registerError = null;
        this.registerSuccess = null;
        document.body.classList.add('auth-modal-open');
      },
      closeRegisterModal() {
        this.showRegisterModal = false;
        this.registerSubmitting = false;
        this.registerError = null;
        this.registerSuccess = null;
        this.registerForm.password = '';
        this.registerForm.confirmPassword = '';
        document.body.classList.remove('auth-modal-open');
      },
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
        let token = null;
        try {
          const res = await fetch('/api/csrf', {
            method: 'GET',
            credentials: 'same-origin',
            cache: 'no-store'
          });
          if (res.ok) {
            const data = await res.json();
            token = data && typeof data.token === 'string' ? data.token : null;
            if (token && !this.getCookie('XSRF-TOKEN')) {
              document.cookie = `XSRF-TOKEN=${encodeURIComponent(token)}; path=/; SameSite=Lax`;
            }
          }
        } catch (_) {}
        return token || this.getCookie('XSRF-TOKEN');
      },
      async readApiErrorMessage(response, fallbackMessage) {
        let message = fallbackMessage;
        try {
          const contentType = (response.headers && response.headers.get('content-type')) || '';
          if (contentType.includes('application/json')) {
            const data = await response.json();
            const code = data && typeof data.code === 'string' ? data.code.trim().toUpperCase() : '';
            if (code === 'CSRF_MISSING' || code === 'CSRF_INVALID' || code === 'CSRF_FAILED') {
              return 'CSRF token 無效或已過期，請重新整理後再試。';
            }
            if (data && typeof data.message === 'string' && data.message.trim()) {
              return data.message.trim();
            }
            const fields = data && data.details && data.details.fields;
            if (fields && typeof fields === 'object') {
              const firstKey = Object.keys(fields)[0];
              if (firstKey && fields[firstKey]) {
                return String(fields[firstKey]);
              }
            }
            return message;
          }
          const text = await response.text();
          if (text && text.trim() && !/<!doctype html/i.test(text)) {
            return text.trim().slice(0, 300);
          }
        } catch (_) {}
        return message;
      },
      async submitRegister() {
        if (this.registerSubmitting) return;
        this.registerError = null;
        this.registerSuccess = null;

        const nickname = (this.registerForm.nickname || '').trim();
        const password = this.registerForm.password || '';
        const confirmPassword = this.registerForm.confirmPassword || '';
        if (!nickname) {
          this.registerError = '請輸入帳號。';
          return;
        }
        if (!/^[A-Za-z0-9]+$/.test(nickname)) {
          this.registerError = '帳號只能使用英文與數字。';
          return;
        }
        if (!password) {
          this.registerError = '請輸入密碼。';
          return;
        }
        if (password !== confirmPassword) {
          this.registerError = '兩次輸入的密碼不一致。';
          return;
        }

        this.registerSubmitting = true;
        try {
          let xsrf = await this.ensureCsrfCookie();

          const doRegister = async (token) => {
            return fetch('/api/auth/member/register', {
              method: 'POST',
              credentials: 'same-origin',
              headers: {
                'Content-Type': 'application/json',
                ...(token ? { 'X-XSRF-TOKEN': token } : {})
              },
              body: JSON.stringify({ nickname, password })
            });
          };

          let res = await doRegister(xsrf);
          if (res.status === 403) {
            xsrf = await this.ensureCsrfCookie();
            res = await doRegister(xsrf);
          }

          if (!res.ok) {
            let message = await this.readApiErrorMessage(res, '註冊失敗，請稍後再試。');
            if (res.status === 403 && (!message || message === 'Forbidden')) {
              message = 'CSRF token 無效或已過期，請重新整理頁面後再試。';
            }
            this.registerError = message;
            return;
          }

          this.registerSuccess = '註冊成功，請使用新帳號登入會員。';
          this.registerForm.password = '';
          this.registerForm.confirmPassword = '';
        } catch (err) {
          this.registerError = (err && err.message) ? err.message : '註冊失敗，請稍後再試。';
        } finally {
          this.registerSubmitting = false;
        }
      },
      async logoutMember() {
        this.closeMenus();
        this.actionError = null;
        try {
          let xsrf = await this.ensureCsrfCookie();
          if (!xsrf) xsrf = this.getCookie('XSRF-TOKEN');
          const doLogout = async (token) => {
            return fetch('/member/logout', {
              method: 'POST',
              credentials: 'same-origin',
              headers: { ...(token ? { 'X-XSRF-TOKEN': token } : {}) }
            });
          };
          let res = await doLogout(xsrf);
          if (res.status === 403) {
            xsrf = await this.ensureCsrfCookie();
            if (!xsrf) xsrf = this.getCookie('XSRF-TOKEN');
            res = await doLogout(xsrf);
          }
          if (!res.ok && res.status !== 302) {
            let message = await this.readApiErrorMessage(res, `登出失敗（${res.status}）`);
            if (res.status === 403) {
              message = '會員登出失敗：CSRF token 無效或已過期，請重新整理後再試。';
            }
            this.actionError = message;
            return;
          }
        } catch (_) {
          this.actionError = '登出失敗，請稍後再試。';
          return;
        }
        await refreshMemberAuth();
        window.location.href = '/';
      },
      async logoutEmployee() {
        this.closeMenus();
        this.actionError = null;
        try {
          let xsrf = await this.ensureCsrfCookie();
          if (!xsrf) xsrf = this.getCookie('XSRF-TOKEN');
          const doLogout = async (token) => {
            return fetch('/employee/logout', {
              method: 'POST',
              credentials: 'same-origin',
              headers: { ...(token ? { 'X-XSRF-TOKEN': token } : {}) }
            });
          };
          let res = await doLogout(xsrf);
          if (res.status === 403) {
            xsrf = await this.ensureCsrfCookie();
            if (!xsrf) xsrf = this.getCookie('XSRF-TOKEN');
            res = await doLogout(xsrf);
          }
          if (!res.ok && res.status !== 302) {
            let message = await this.readApiErrorMessage(res, `員工登出失敗（${res.status}）`);
            if (res.status === 403) {
              message = '員工登出失敗：CSRF token 無效或已過期，請重新整理後再試。';
            }
            this.actionError = message;
            return;
          }
        } catch (_) {
          this.actionError = '員工登出失敗，請稍後再試。';
          return;
        }
        await refreshMemberAuth();
        window.location.href = '/';
      }
    },
    async mounted() {
      await refreshMemberAuth();
      this.onDocumentClick = this.handleOutsideClick.bind(this);
      this.onEscapeKey = this.handleEscape.bind(this);
      document.addEventListener('click', this.onDocumentClick);
      document.addEventListener('keydown', this.onEscapeKey);
    },
    unmounted() {
      if (this.onDocumentClick) {
        document.removeEventListener('click', this.onDocumentClick);
      }
      if (this.onEscapeKey) {
        document.removeEventListener('keydown', this.onEscapeKey);
      }
      document.body.classList.remove('auth-modal-open');
    }
  };

  const Hero = {
    props: {
      slides: {
        type: Array,
        default: () => []
      },
      autoPlayMs: {
        type: Number,
        default: 4500
      }
    },
    data() {
      return {
        activeIndex: 0,
        autoPlayTimer: null,
        hovering: false
      };
    },
    computed: {
      normalizedSlides() {
        const raw = Array.isArray(this.slides) ? this.slides : [];
        const valid = raw.filter((item) => item && typeof item === 'object');
        if (valid.length > 0) {
          return valid.map((slide, index) => ({
            key: slide.key || `slide-${index}`,
            movieId: slide.movieId != null ? slide.movieId : null,
            title: slide.title || '現正熱映',
            subtitle: slide.subtitle || '',
            imageUrl: slide.imageUrl || '/images/sleep.jpg'
          }));
        }
        return [
          {
            key: 'fallback-slide',
            movieId: null,
            title: '很好睡電影院',
            subtitle: '',
            imageUrl: '/images/sleep.jpg'
          }
        ];
      },
      canNavigate() {
        return this.normalizedSlides.length > 1;
      },
      trackStyle() {
        return { transform: `translateX(-${this.activeIndex * 100}%)` };
      }
    },
    watch: {
      normalizedSlides(nextSlides) {
        if (!Array.isArray(nextSlides) || nextSlides.length === 0) {
          this.activeIndex = 0;
          this.stopAutoPlay();
          return;
        }
        if (this.activeIndex >= nextSlides.length) {
          this.activeIndex = 0;
        }
        this.restartAutoPlay();
      }
    },
    methods: {
      goToSlide(index) {
        if (!this.canNavigate) return;
        const max = this.normalizedSlides.length;
        if (index < 0 || index >= max) return;
        this.activeIndex = index;
      },
      nextSlide() {
        if (!this.canNavigate) return;
        this.activeIndex = (this.activeIndex + 1) % this.normalizedSlides.length;
      },
      prevSlide() {
        if (!this.canNavigate) return;
        this.activeIndex = (this.activeIndex - 1 + this.normalizedSlides.length) % this.normalizedSlides.length;
      },
      stopAutoPlay() {
        if (this.autoPlayTimer) {
          clearInterval(this.autoPlayTimer);
          this.autoPlayTimer = null;
        }
      },
      startAutoPlay() {
        this.stopAutoPlay();
        if (!this.canNavigate) return;
        this.autoPlayTimer = setInterval(() => {
          if (!this.hovering) {
            this.nextSlide();
          }
        }, Math.max(2500, this.autoPlayMs));
      },
      restartAutoPlay() {
        this.startAutoPlay();
      },
      onMouseEnter() {
        this.hovering = true;
      },
      onMouseLeave() {
        this.hovering = false;
      },
      onImageError(event) {
        if (event && event.target) {
          event.target.src = '/images/sleep.jpg';
        }
      }
    },
    mounted() {
      this.startAutoPlay();
    },
    beforeUnmount() {
      this.stopAutoPlay();
    },
    template: `
      <header
        class="hero hero-carousel"
        @mouseenter="onMouseEnter"
        @mouseleave="onMouseLeave"
      >
        <div class="hero-inner">
          <span class="hero-label">現正熱映中</span>
          <img class="hero-badge" src="/images/sleep.jpg" alt="Very Sleepy Cinema 徽章">
          <div class="hero-text">
            <h1>很好睡電影院</h1>
            <p class="hero-tagline">每個顧客都可以睡得很安穩</p>
          </div>
        </div>

        <div class="hero-carousel-wrap">
          <div class="hero-carousel-viewport">
            <div class="hero-carousel-track" :style="trackStyle">
              <article class="hero-slide" v-for="(slide, index) in normalizedSlides" :key="slide.key">
                <img
                  class="hero-slide-image"
                  :src="slide.imageUrl"
                  :alt="slide.title"
                  loading="eager"
                  @error="onImageError"
                >
                <div class="hero-slide-overlay"></div>
                <div class="hero-slide-content">
                  <h2 class="hero-slide-title">{{ slide.title }}</h2>
                  <p v-if="slide.subtitle" class="hero-slide-subtitle">{{ slide.subtitle }}</p>
                  <router-link
                    v-if="slide.movieId !== null"
                    class="hero-slide-cta"
                    :to="'/movies/' + slide.movieId"
                  >
                    詳閱場次
                  </router-link>
                </div>
              </article>
            </div>

            <button
              v-if="canNavigate"
              type="button"
              class="hero-nav hero-nav-prev"
              aria-label="上一張"
              @click="prevSlide"
            >
              ‹
            </button>
            <button
              v-if="canNavigate"
              type="button"
              class="hero-nav hero-nav-next"
              aria-label="下一張"
              @click="nextSlide"
            >
              ›
            </button>
          </div>

          <div v-if="canNavigate" class="hero-dots" aria-label="輪播指示">
            <button
              v-for="(slide, index) in normalizedSlides"
              :key="'dot-' + slide.key"
              type="button"
              class="hero-dot"
              :class="{ active: index === activeIndex }"
              :aria-label="'第 ' + (index + 1) + ' 張'"
              @click="goToSlide(index)"
            ></button>
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
