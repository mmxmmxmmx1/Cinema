const { createApp } = Vue;
const { createRouter, createWebHistory } = VueRouter;

const shared = window.CinemaSpaShared;
if (!shared) {
  throw new Error('CinemaSpaShared module is not loaded.');
}

const {
  LoadingState,
  ErrorState,
  refreshMemberAuth,
  currentSpaPath,
  refreshBookingWindow,
  BookingWindowBanner,
  GlobalAuthWidget,
  Hero
} = shared;

const HomePage = {
  template: `
    <div class="app-shell">
      <Hero v-if="!loading && !error && heroSlides.length > 0" :slides="heroSlides" />
      <LoadingState v-if="loading" />
      <ErrorState v-else-if="error" :message="error" />
      <section v-else class="movie-grid">
        <article v-for="movie in movies" :key="movie.id" class="movie-card">
          <img :src="movie.posterUrl" :alt="movie.title" />
          <div class="card-body">
            <h3>{{ movie.title }}</h3>
            <p>{{ movie.subtitle }}</p>
            <router-link class="primary-link" :to="'/movies/' + movie.id">詳閱場次</router-link>
          </div>
        </article>
      </section>
    </div>
  `,
  components: { Hero, LoadingState, ErrorState },
  data() {
    return { movies: [], loading: true, error: null };
  },
  computed: {
    heroSlides() {
      if (!Array.isArray(this.movies) || this.movies.length === 0) {
        return [];
      }
      const preferredOrder = ['mv-01', 'mv-02', 'mv-03', 'mv-04', 'mv-05', 'mv-06', 'mv-07', 'mv-08', 'mv-09', 'mv-10'];
      const byId = new Map(this.movies.map((movie) => [movie && movie.id, movie]));
      const preferredMovies = preferredOrder
        .map((id) => byId.get(id))
        .filter((movie) => movie && (movie.carouselImageUrl || movie.posterUrl));
      const fallbackMovies = this.movies.filter(
        (movie) => movie && (movie.carouselImageUrl || movie.posterUrl) && !preferredOrder.includes(movie.id)
      );
      const selectedMovies = [...preferredMovies, ...fallbackMovies].slice(0, 10);

      return selectedMovies.map((movie, index) => ({
        key: movie && movie.id != null ? `movie-${movie.id}` : `movie-fallback-${index}`,
        movieId: movie && movie.id != null ? movie.id : null,
        title: movie && movie.title ? movie.title : '現正熱映',
        subtitle: movie && movie.subtitle ? movie.subtitle : '',
        imageUrl: movie && (movie.carouselImageUrl || movie.posterUrl)
          ? (movie.carouselImageUrl || movie.posterUrl)
          : '/images/sleep.jpg'
      }));
    }
  },
  async mounted() {
    try {
      const res = await fetch('/api/movies');
      if (!res.ok) throw new Error('Failed to load movie list');
      this.movies = await res.json();
    } catch (err) {
      this.error = err.message || 'Unknown error';
    } finally {
      this.loading = false;
    }
  }
};

const MovieDetailPage = {
  template: `
    <div class="app-shell">
      <div class="page">
        <LoadingState v-if="loading" />
        <template v-else-if="error || !movie">
          <ErrorState :message="error || 'Movie not found'" />
          <button class="primary-link" @click="goBack">返回</button>
        </template>
        <template v-else>
          <button class="back-link" @click="goBack">返回</button>
          <div class="movie-detail-card">
            <img :src="movie.posterUrl" :alt="movie.title" />
            <div>
              <h2>{{ movie.title }}</h2>
              <p>{{ movie.description }}</p>
              <h3>場次資訊</h3>
              <div v-if="!movie.showtimes || movie.showtimes.length === 0" class="error-state" style="padding: 1rem 0; text-align: left;">
                目前沒有可訂購的場次（每日 07:00 - 22:45 開放）。
              </div>
              <ul v-else class="showtime-list">
                <li v-for="showtime in movie.showtimes" :key="showtime.id" class="showtime-item">
                  <div class="showtime-meta">
                    <strong>{{ showtime.startTime }}</strong>
                    <span>{{ showtime.durationMinutes }} 分鐘</span>
                    <span>{{ showtime.auditorium }}</span>
                  </div>
                  <router-link class="primary-link" :to="'/movies/' + movie.id + '/showtimes/' + showtime.id">選擇座位</router-link>
                </li>
              </ul>
            </div>
          </div>
        </template>
      </div>
    </div>
  `,
  components: { LoadingState, ErrorState },
  data() { return { movie: null, loading: true, error: null }; },
  async mounted() {
    try {
      const res = await fetch(`/api/movies/${this.$route.params.movieId}`);
      if (!res.ok) throw new Error('Failed to load movie details');
      this.movie = await res.json();
    } catch (err) {
      this.error = err.message || 'Failed to load movie content';
    } finally {
      this.loading = false;
    }
  },
  methods: { goBack() { this.$router.go(-1); } }
};

const SeatSelectionPage = {
  template: `
    <div class="app-shell">
      <div class="page">
        <LoadingState v-if="loading" />
        <template v-else-if="error || !movie || !details">
          <ErrorState :message="error || 'Seat info unavailable'" />
          <button class="primary-link" @click="goBack">返回</button>
        </template>
        <template v-else>
          <button class="back-link" @click="goBack">返回</button>
          <div class="seat-selection">
            <div class="seat-selection-layout">
              <div class="movie-info-section">
                <img :src="movie.posterUrl" :alt="movie.title" class="movie-poster" />
                <div class="movie-info">
                  <h2>{{ movie.title }}</h2>
                  <p>{{ details.showtime.startTime }} · {{ details.showtime.auditorium }}</p>
                  <p>{{ details.showtime.durationMinutes }} 分鐘</p>
                </div>
              </div>
              <div class="seat-area">
                <div class="seat-grid" role="grid">
                  <div v-for="(row, rowIndex) in seatRows" :key="rowIndex" class="seat-row">
                    <span class="row-label">{{ String.fromCharCode(65 + rowIndex) }}</span>
                    <div class="seat-row-grid">
                      <button v-for="seat in row" :key="seat.seatId" type="button" :class="getSeatClass(seat)" @click="toggleSeat(seat.seatId, seat.reserved)">
                        {{ seat.seatId.substring(1) }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="selection-summary">
              <div v-if="purchaseError" class="error-state" style="margin-bottom: 10px;">{{ purchaseError }}</div>
              <div v-if="purchaseSuccess" class="loading" style="margin-bottom: 10px;">{{ purchaseSuccess }}</div>
              <div>
                <strong>已選座位：</strong>
                {{ selectedSeats.length > 0 ? selectedSeats.join(', ') : '尚未選擇' }}
              </div>
              <button type="button" :disabled="selectedSeats.length === 0 || purchasing" @click="confirmBooking">
                {{ purchasing ? '處理中…' : '確認訂票' }}
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>
  `,
  components: { LoadingState, ErrorState },
  data() {
    return {
      movie: null,
      details: null,
      loading: true,
      error: null,
      selectedSeats: [],
      purchasing: false,
      purchaseError: null,
      purchaseSuccess: null
    };
  },
  computed: {
    seatRows() {
      if (!this.details || !this.details.seatLayout) return [];
      const { seatLayout } = this.details;
      const rows = [];
      for (let row = 0; row < seatLayout.rows; row++) {
        const start = row * seatLayout.columns;
        rows.push(seatLayout.seats.slice(start, start + seatLayout.columns));
      }
      return rows;
    }
  },
  async mounted() {
    await this.loadSeatData();
  },
  methods: {
    async loadSeatData() {
      this.loading = true;
      this.error = null;
      this.purchaseError = null;
      this.purchaseSuccess = null;
      try {
        const [movieRes, showtimeRes] = await Promise.all([
          fetch(`/api/movies/${this.$route.params.movieId}`),
          fetch(`/api/movies/${this.$route.params.movieId}/showtimes/${this.$route.params.showtimeId}`)
        ]);
        if (!movieRes.ok) throw new Error('Failed to load movie');
        if (!showtimeRes.ok) {
          if (showtimeRes.status === 410 || showtimeRes.status === 403 || showtimeRes.status === 404) {
            throw new Error('目前非訂票時段或場次已開演，無法訂票。');
          }
          throw new Error('Failed to load showtime');
        }
        this.movie = await movieRes.json();
        this.details = await showtimeRes.json();
      } catch (err) {
        this.error = err.message || 'Failed to load seat data';
      } finally {
        this.loading = false;
      }
    },
    toggleSeat(seatId, reserved) {
      if (reserved) return;
      const index = this.selectedSeats.indexOf(seatId);
      if (index > -1) {
        this.selectedSeats.splice(index, 1);
        return;
      }

      // Per-order limit: max 4 tickets.
      if (this.selectedSeats.length >= 4) {
        this.purchaseError = '一次最多只能買 4 張票。';
        return;
      }

      this.purchaseError = null;
      this.purchaseSuccess = null;
      this.selectedSeats.push(seatId);
    },
    getSeatClass(seat) {
      const classes = ['seat'];
      classes.push(seat.reserved ? 'reserved' : 'available');
      if (this.selectedSeats.includes(seat.seatId)) classes.push('selected');
      return classes.join(' ');
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
    async confirmBooking() {
      this.purchaseError = null;
      this.purchaseSuccess = null;

      if (!this.selectedSeats || this.selectedSeats.length === 0) {
        this.purchaseError = '請先選擇座位。';
        return;
      }
      if (this.selectedSeats.length > 4) {
        this.purchaseError = '一次最多只能買 4 張票。';
        return;
      }

      const booking = await refreshBookingWindow();
      if (booking && booking.bookingOpen === false) {
        this.purchaseError = booking.message || '目前非訂票時段（每日 07:00 - 22:45）。';
        return;
      }

      try {
        const checkoutPath = `/checkout/${encodeURIComponent(this.$route.params.movieId)}/showtimes/${encodeURIComponent(this.$route.params.showtimeId)}`;

        sessionStorage.setItem('pendingCheckout', JSON.stringify({
          returnTo: checkoutPath,
          seatIds: this.selectedSeats
        }));

        // Check member login before attempting a purchase.
        const authRes = await fetch('/api/auth/member', { credentials: 'same-origin' });
        const authJson = authRes.ok ? await authRes.json() : { authenticated: false };
        if (!authJson || !authJson.authenticated) {
          window.location.href = `/member/login?returnTo=${encodeURIComponent(checkoutPath)}`;
          return;
        }
        this.$router.push(checkoutPath);
      } catch (err) {
        this.purchaseError = err && err.message ? err.message : '無法前往結帳頁';
      }
    },
    goBack() { this.$router.go(-1); }
  }
};

const CheckoutPage = {
  template: `
    <div class="app-shell">
      <div class="page">
        <button class="back-link" @click="goBack">返回</button>
        <LoadingState v-if="loading" />
        <template v-else-if="error">
          <ErrorState :message="error" />
          <button class="primary-link" @click="goToSeatSelection">回到選位</button>
        </template>
        <template v-else>
          <div class="movie-detail-card">
            <img :src="movie.posterUrl" :alt="movie.title" />
            <div>
              <h2>結帳</h2>
              <p><strong>電影：</strong>{{ movie.title }}</p>
              <p><strong>場次：</strong>{{ details.showtime.startTime }} · {{ details.showtime.auditorium }}</p>
              <p><strong>座位：</strong>{{ seatIds.join(', ') }}</p>
              <p><strong>張數：</strong>{{ seatIds.length }}</p>
              <p><strong>票價：</strong>{{ unitPrice }} / 張</p>
              <p><strong>總價：</strong>{{ totalPrice }}</p>
              <p v-if="alreadyPaidOrder" style="margin-bottom: 0.5rem;">
                <strong>付款狀態：</strong>
                已付款（訂單 #{{ alreadyPaidOrder.orderId }}）
              </p>
              <p v-else style="margin-bottom: 0.5rem;">
                <strong>付款模擬：</strong>
                <select v-model="paymentMode">
                  <option value="SUCCESS">成功</option>
                  <option value="FAILED">失敗</option>
                  <option value="TIMEOUT">逾時</option>
                </select>
              </p>

              <div v-if="payError" class="error-state" style="margin-top: 14px;">{{ payError }}</div>
              <div v-if="paySuccess" class="loading" style="margin-top: 14px;">{{ paySuccess }}</div>

              <div style="margin-top: 16px; display: flex; gap: 12px; flex-wrap: wrap;">
                <button v-if="!alreadyPaidOrder && !paySuccess" type="button" :disabled="paying" @click="confirmPay">
                  {{ paying ? '付款處理中…' : '確認付款' }}
                </button>
                <button v-if="paySuccess && alreadyPaidOrder" type="button" class="primary-link" disabled>
                  已完成付款
                </button>
                <button type="button" class="primary-link" @click="goToOrders">我的訂單</button>
                <button v-if="alreadyPaidOrder" type="button" class="primary-link" @click="goToOrderDetail">查看該筆訂單</button>
              </div>
              <p style="margin-top: 12px; color: #b9bedb; line-height: 1.6;">
                規則：一次最多 4 張；6 小時內最多 4 張，且只能同一個影廳，不能跨影廳。
              </p>
            </div>
          </div>
        </template>
      </div>
    </div>
  `,
  components: { LoadingState, ErrorState },
  data() {
    return {
      movie: null,
      details: null,
      seatIds: [],
      loading: true,
      error: null,
      paying: false,
      payError: null,
      paySuccess: null,
      unitPrice: 300,
      totalPrice: 0,
      paymentMode: 'SUCCESS',
      alreadyPaidOrder: null,
      lastApiErrorCode: null
    };
  },
  async mounted() {
    // If the user is not logged in as MEMBER, redirect to the member login page.
    const authJson = await refreshMemberAuth();
    if (!authJson || !authJson.authenticated) {
      window.location.href = `/member/login?returnTo=${encodeURIComponent(this.checkoutPath())}`;
      return;
    }
    await this.loadCheckout();
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
          // Fallback: if browser did not persist Set-Cookie for any reason, set it manually.
          if (token && !this.getCookie('XSRF-TOKEN')) {
            document.cookie = `XSRF-TOKEN=${encodeURIComponent(token)}; path=/; SameSite=Lax`;
          }
        }
      } catch (_) {}
      return token || this.getCookie('XSRF-TOKEN');
    },
    async readApiErrorMessage(response, fallbackMessage) {
      let message = fallbackMessage;
      this.lastApiErrorCode = null;
      try {
        const contentType = (response.headers && response.headers.get('content-type')) || '';
        if (contentType.includes('application/json')) {
          const data = await response.json();
          const code = data && typeof data.code === 'string' ? data.code.trim() : '';
          if (code) {
            this.lastApiErrorCode = code;
          }
          const serverMessage = data && typeof data.message === 'string' ? data.message.trim() : '';
          const serverError = data && typeof data.error === 'string' ? data.error.trim() : '';
          if (code && (!serverMessage || serverMessage === 'Forbidden')) {
            const mapped = this.mapApiErrorByCode(code);
            if (mapped) return mapped;
          }
          if (serverMessage) return serverMessage;
          if (serverError) return serverError;
          return message;
        }
        const text = await response.text();
        if (text && text.trim() && !/<!doctype html/i.test(text)) {
          return text.trim().slice(0, 300);
        }
      } catch (_) {}
      return message;
    },
    mapApiErrorByCode(code) {
      const safe = typeof code === 'string' ? code.trim().toUpperCase() : '';
      if (!safe) return '';
      if (safe === 'CSRF_MISSING' || safe === 'CSRF_INVALID' || safe === 'CSRF_FAILED') {
        return 'CSRF token 無效或已過期，請重新整理後再試。';
      }
      if (safe === 'MEMBER_ACCESS_DENIED') {
        return '沒有會員權限，請確認已使用會員帳號登入。';
      }
      if (safe === 'TICKET_RULE_VIOLATION') {
        return '不符合購票規則，請檢查座位、張數與場次限制。';
      }
      if (safe === 'TICKET_CONFLICT') {
        return '座位已被其他人預訂，請重新選位。';
      }
      if (safe === 'RATE_LIMIT_EXCEEDED') {
        return '操作過於頻繁，請稍後再試。';
      }
      return '';
    },
    isCsrfRelatedMessage(message) {
      const text = typeof message === 'string' ? message : '';
      return /csrf|xsrf|token/i.test(text);
    },
    async resolveForbiddenMessage(defaultMessage, serverMessage) {
      const message = (serverMessage || '').trim();
      const checkJson = await refreshMemberAuth();
      if (!checkJson || !checkJson.authenticated) {
        window.location.href = `/member/login?returnTo=${encodeURIComponent(this.checkoutPath())}`;
        return null;
      }
      if (checkJson.employee && !checkJson.member) {
        return `Forbidden：你目前是員工登入（${checkJson.username || ''}），請先員工登出後再用會員登入。`;
      }
      if (!message || message === defaultMessage || message === 'Forbidden' || this.isCsrfRelatedMessage(message)) {
        const byCode = this.mapApiErrorByCode(this.lastApiErrorCode);
        return byCode || 'CSRF token 無效或已過期，請重新整理後再試。';
      }
      return message;
    },
    checkoutPath() {
      return `/checkout/${encodeURIComponent(this.$route.params.movieId)}/showtimes/${encodeURIComponent(this.$route.params.showtimeId)}`;
    },
    seatSelectionPath() {
      return `/movies/${encodeURIComponent(this.$route.params.movieId)}/showtimes/${encodeURIComponent(this.$route.params.showtimeId)}`;
    },
    async loadCheckout() {
      this.loading = true;
      this.error = null;
      this.payError = null;
      this.paySuccess = null;
      this.alreadyPaidOrder = null;
      try {
        const [movieRes, showtimeRes] = await Promise.all([
          fetch(`/api/movies/${this.$route.params.movieId}`),
          fetch(`/api/movies/${this.$route.params.movieId}/showtimes/${this.$route.params.showtimeId}`)
        ]);
        if (!movieRes.ok) throw new Error('Failed to load movie');
        if (!showtimeRes.ok) {
          if (showtimeRes.status === 410 || showtimeRes.status === 403 || showtimeRes.status === 404) {
            throw new Error('目前非訂票時段或場次已開演，無法訂票。');
          }
          throw new Error('Failed to load showtime');
        }
        this.movie = await movieRes.json();
        this.details = await showtimeRes.json();

        const raw = sessionStorage.getItem('pendingCheckout');
        if (!raw) {
          const existingPaidOrder = await this.findLatestPaidOrderForCurrentShowtime();
          if (existingPaidOrder) {
            this.alreadyPaidOrder = existingPaidOrder;
            this.seatIds = Array.isArray(existingPaidOrder.seatIds) ? existingPaidOrder.seatIds : [];
            this.unitPrice = typeof existingPaidOrder.unitPrice === 'number' ? existingPaidOrder.unitPrice : 300;
            this.totalPrice = typeof existingPaidOrder.totalPrice === 'number'
              ? existingPaidOrder.totalPrice
              : this.seatIds.length * this.unitPrice;
            this.paySuccess = `此場次你已完成付款（訂單 #${existingPaidOrder.orderId}）。不能重複購買相同已成立座位。`;
            return;
          }
          throw new Error('找不到結帳資料，請回到選位重新操作。');
        }
        const pending = JSON.parse(raw);
        if (!pending || pending.returnTo !== this.checkoutPath()) {
          throw new Error('結帳資料不一致，請回到選位重新操作。');
        }
        const seats = Array.isArray(pending.seatIds) ? pending.seatIds : [];
        const normalized = [];
        for (const seatId of seats) {
          if (!seatId) continue;
          const s = String(seatId).trim();
          if (!s) continue;
          if (normalized.includes(s)) continue;
          normalized.push(s);
          if (normalized.length >= 4) break;
        }
        if (normalized.length === 0) {
          throw new Error('請至少選擇 1 個座位。');
        }
        this.seatIds = normalized;
        this.unitPrice = 300;
        this.totalPrice = this.seatIds.length * this.unitPrice;
      } catch (err) {
        this.error = err && err.message ? err.message : '無法載入結帳頁';
      } finally {
        this.loading = false;
      }
    },
    async confirmPay() {
      if (this.paying) return;
      if (this.alreadyPaidOrder) {
        this.paySuccess = `此場次已付款（訂單 #${this.alreadyPaidOrder.orderId}），不可重複購買。`;
        return;
      }
      this.payError = null;
      this.paySuccess = null;

        this.paying = true;
      try {
        const booking = await refreshBookingWindow();
        if (booking && booking.bookingOpen === false) {
          this.payError = booking.message || '目前非訂票時段（每日 07:00 - 22:45）。';
          return;
        }

        const authJson = await refreshMemberAuth();
        if (!authJson || !authJson.authenticated) {
          // Common case: user is not logged in as MEMBER (or is logged in as EMPLOYEE only).
          // Member APIs will return 403 in that situation.
          window.location.href = `/member/login?returnTo=${encodeURIComponent(this.checkoutPath())}`;
          return;
        }

        let xsrf = await this.ensureCsrfCookie();

        const createOrder = async (token) => {
          return fetch('/member/api/orders', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
              'Content-Type': 'application/json',
              ...(token ? { 'X-XSRF-TOKEN': token } : {})
            },
            body: JSON.stringify({
              movieId: this.$route.params.movieId,
              showtimeId: this.$route.params.showtimeId,
              seatIds: this.seatIds
            })
          });
        };

        let createRes = await createOrder(xsrf);
        if (createRes.status === 403) {
          // If CSRF token was missing/stale, refresh and retry once.
          const refreshed = await this.ensureCsrfCookie();
          xsrf = refreshed || xsrf;
          createRes = await createOrder(xsrf);
        }

        if (!createRes.ok) {
          let msg = '建立訂單失敗';
          msg = await this.readApiErrorMessage(createRes, msg);
          if (createRes.status === 403) {
            msg = await this.resolveForbiddenMessage('建立訂單失敗', msg);
            if (!msg) return;
          }
          this.payError = msg;
          return;
        }

        const created = await createRes.json();
        const orderId = created && created.orderId ? created.orderId : null;
        if (!orderId) {
          this.payError = '建立訂單失敗：沒有回傳訂單編號';
          return;
        }
        if (typeof created.unitPrice === 'number') this.unitPrice = created.unitPrice;
        if (typeof created.totalPrice === 'number') this.totalPrice = created.totalPrice;

        const idempotencyStorageKey = `pay-idempotency:${orderId}`;
        let paymentIdempotencyKey = sessionStorage.getItem(idempotencyStorageKey);
        if (!paymentIdempotencyKey) {
          paymentIdempotencyKey = (window.crypto && typeof window.crypto.randomUUID === 'function')
            ? window.crypto.randomUUID()
            : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
          sessionStorage.setItem(idempotencyStorageKey, paymentIdempotencyKey);
        }

        const payOrder = async (token) => {
          return fetch(`/member/api/orders/${orderId}/pay?mode=${encodeURIComponent(this.paymentMode)}`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
              ...(token ? { 'X-XSRF-TOKEN': token } : {}),
              'Idempotency-Key': paymentIdempotencyKey
            }
          });
        };

        let payRes = await payOrder(xsrf);
        if (payRes.status === 403) {
          const refreshed = await this.ensureCsrfCookie();
          xsrf = refreshed || xsrf;
          payRes = await payOrder(xsrf);
        }

        if (!payRes.ok) {
          let msg = '付款失敗';
          msg = await this.readApiErrorMessage(payRes, msg);
          if (payRes.status === 403) {
            msg = await this.resolveForbiddenMessage('付款失敗', msg);
            if (!msg) return;
          }
          this.payError = msg;
          return;
        }

        const paid = await payRes.json();
        if (paid) {
          if (typeof paid.unitPrice === 'number') this.unitPrice = paid.unitPrice;
          if (typeof paid.totalPrice === 'number') this.totalPrice = paid.totalPrice;
        }
        if (!paid || paid.status !== 'PAID') {
          const reason = paid && typeof paid.failureReason === 'string' && paid.failureReason
            ? paid.failureReason
            : (paid && paid.status ? `狀態為 ${paid.status}` : '付款未完成');
          this.payError = `付款未完成：${reason}`;
          // EXPIRED means this order can no longer be paid.
          if (paid && paid.status === 'EXPIRED') {
            sessionStorage.removeItem('pendingCheckout');
          }
          return;
        }
        this.paySuccess = `付款成功，訂單編號 #${paid.orderId}`;
        this.alreadyPaidOrder = {
          orderId: paid.orderId || orderId,
          status: 'PAID',
          movieId: String(this.$route.params.movieId),
          showtimeId: String(this.$route.params.showtimeId),
          seatIds: Array.isArray(this.seatIds) ? [...this.seatIds] : [],
          unitPrice: this.unitPrice,
          totalPrice: this.totalPrice
        };
        sessionStorage.removeItem(idempotencyStorageKey);
        sessionStorage.removeItem('pendingCheckout');
      } catch (err) {
        this.payError = err && err.message ? err.message : '付款失敗';
      } finally {
        this.paying = false;
      }
    },
    goBack() { this.$router.go(-1); },
    goToSeatSelection() { this.$router.push(this.seatSelectionPath()); },
    goToOrders() { window.location.href = '/member/orders'; },
    goToOrderDetail() {
      if (this.alreadyPaidOrder && this.alreadyPaidOrder.orderId) {
        window.location.href = `/member/orders/${this.alreadyPaidOrder.orderId}`;
        return;
      }
      this.goToOrders();
    },
    async findLatestPaidOrderForCurrentShowtime() {
      try {
        const res = await fetch('/member/api/orders', { credentials: 'same-origin' });
        if (!res.ok) return null;
        const orders = await res.json();
        if (!Array.isArray(orders) || orders.length === 0) return null;

        const paid = orders.find(o =>
          o &&
          o.status === 'PAID' &&
          o.movieId === this.$route.params.movieId &&
          o.showtimeId === this.$route.params.showtimeId);
        if (!paid || !paid.orderId) return null;

        const detailRes = await fetch(`/member/api/orders/${paid.orderId}`, { credentials: 'same-origin' });
        if (!detailRes.ok) return null;
        const detail = await detailRes.json();
        if (!detail || detail.status !== 'PAID') return null;
        return detail;
      } catch (_) {
        return null;
      }
    }
  }
};

const OrdersPage = {
  template: `
    <div class="app-shell">
      <div class="page">
        <button class="back-link" @click="goBack">返回</button>
        <h2 style="margin-top: 0;">我的訂單</h2>
        <LoadingState v-if="loading" />
        <ErrorState v-else-if="error" :message="error" />
        <template v-else>
          <div v-if="orders.length === 0" class="loading">目前沒有訂單</div>
          <ul v-else class="showtime-list" style="list-style: none; padding: 0;">
            <li v-for="o in orders" :key="o.orderId" class="showtime-item">
              <div class="showtime-meta">
                <strong>#{{ o.orderId }}</strong>
                <span>{{ o.auditorium }}</span>
                <span>{{ o.status }}</span>
                <span>{{ o.totalQty }} 張</span>
              </div>
              <router-link class="primary-link" :to="'/orders/' + o.orderId">查看</router-link>
            </li>
          </ul>
        </template>
      </div>
    </div>
  `,
  components: { LoadingState, ErrorState },
  data() { return { orders: [], loading: true, error: null }; },
  async mounted() {
    const authJson = await refreshMemberAuth();
    if (!authJson || !authJson.authenticated) {
      window.location.href = `/member/login?returnTo=${encodeURIComponent(currentSpaPath())}`;
      return;
    }
    try {
      const res = await fetch('/member/api/orders', { credentials: 'same-origin' });
      if (!res.ok) throw new Error('Failed to load orders');
      this.orders = await res.json();
    } catch (err) {
      this.error = err.message || '無法載入訂單';
    } finally {
      this.loading = false;
    }
  },
  methods: { goBack() { this.$router.go(-1); } }
};

const OrderDetailPage = {
  template: `
    <div class="app-shell">
      <div class="page">
        <button class="back-link" @click="goBack">返回</button>
        <LoadingState v-if="loading" />
        <ErrorState v-else-if="error" :message="error" />
        <template v-else>
          <h2 style="margin-top: 0;">訂單 #{{ order.orderId }}</h2>
          <p><strong>狀態：</strong>{{ order.status }}</p>
          <p><strong>影廳：</strong>{{ order.auditorium }}</p>
          <p><strong>座位：</strong>{{ order.seatIds.join(', ') }}</p>
          <div style="margin-top: 16px; display: flex; gap: 12px; flex-wrap: wrap;">
            <button v-if="order.status === 'PENDING'" type="button" @click="cancel">取消訂單</button>
            <button type="button" class="primary-link" @click="goOrders">回列表</button>
          </div>
          <div v-if="actionError" class="error-state" style="margin-top: 14px;">{{ actionError }}</div>
          <div v-if="actionSuccess" class="loading" style="margin-top: 14px;">{{ actionSuccess }}</div>
        </template>
      </div>
    </div>
  `,
  components: { LoadingState, ErrorState },
  data() { return { order: null, loading: true, error: null, actionError: null, actionSuccess: null }; },
  async mounted() {
    const authJson = await refreshMemberAuth();
    if (!authJson || !authJson.authenticated) {
      window.location.href = `/member/login?returnTo=${encodeURIComponent(currentSpaPath())}`;
      return;
    }
    await this.loadDetail();
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
      const xsrf = this.getCookie('XSRF-TOKEN');
      if (xsrf) return;
      try { await fetch('/api/csrf', { credentials: 'same-origin' }); } catch (_) {}
    },
    async loadDetail() {
      this.loading = true;
      this.error = null;
      this.actionError = null;
      this.actionSuccess = null;
      try {
        const res = await fetch(`/member/api/orders/${this.$route.params.orderId}`, { credentials: 'same-origin' });
        if (!res.ok) throw new Error('Failed to load order');
        this.order = await res.json();
      } catch (err) {
        this.error = err.message || '無法載入訂單';
      } finally {
        this.loading = false;
      }
    },
    async cancel() {
      this.actionError = null;
      this.actionSuccess = null;
      try {
        await this.ensureCsrfCookie();
        const xsrf = this.getCookie('XSRF-TOKEN');
        const res = await fetch(`/member/api/orders/${this.$route.params.orderId}/cancel`, {
          method: 'POST',
          credentials: 'same-origin',
          headers: {
            ...(xsrf ? { 'X-XSRF-TOKEN': xsrf } : {})
          }
        });
        if (!res.ok) {
          let msg = '取消失敗';
          try {
            const data = await res.json();
            msg = data && data.message ? data.message : msg;
          } catch (_) {}
          this.actionError = msg;
          return;
        }
        this.order = await res.json();
        this.actionSuccess = '已取消訂單';
      } catch (err) {
        this.actionError = err && err.message ? err.message : '取消失敗';
      }
    },
    goBack() { this.$router.go(-1); },
    goOrders() { this.$router.push('/orders'); }
  }
};

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
    <div class="root-shell">
      <GlobalAuthWidget />
      <BookingWindowBanner />
      <router-view></router-view>
    </div>
  `,
  components: { GlobalAuthWidget, BookingWindowBanner }
};

const app = createApp(RootApp);
app.use(router);
app.mount('#root');
