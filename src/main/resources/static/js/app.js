const { createApp } = Vue;
const { createRouter, createWebHistory } = VueRouter;

const LoadingState = {
  template: '<div class="loading">載入中...</div>'
};

const ErrorState = {
  template: '<div class="error-state">{{ message }}</div>',
  props: ['message']
};

const Hero = {
  template: `
    <header class="hero">
      <div class="hero-inner">
        <span class="hero-label">現正熱映中</span>
        <img class="hero-badge" src="/images/sleep.jpg" alt="Very Sleepy Cinema 徽章">
        <div class="hero-text">
          <h1>很好睡電影院</h1>
          <p>每個顧客都可以睡得很安穩。</p>
        </div>
      </div>
    </header>
  `
};

const GlobalFloatingButtons = {
  template: `
    <div>
      <!--
        Route the login buttons directly to the appropriate login endpoints.
        The previous implementation forwarded '/login?target=member' and
        '/login?target=employee' through a generic '/login' handler, but that
        path was configured to forward back to the SPA index page.  As a
        result, clicking these links never displayed the actual login pages.
        Here we point directly to the dedicated Spring MVC login pages
        implemented in PageController, avoiding any intermediate query
        parameters or view controller forwarding.
      -->
      <a class="floating-button login" href="/member/login">會員登入</a>
      <a class="floating-button employee" href="/employee/login">員工專區</a>
    </div>
  `
};

const HomePage = {
  template: `
    <div class="app-shell">
      <Hero />
      <LoadingState v-if="loading" />
      <ErrorState v-else-if="error" :message="error" />
      <section v-else class="movie-grid">
        <article v-for="movie in movies" :key="movie.id" class="movie-card">
          <img :src="movie.posterUrl" :alt="movie.title" />
          <div class="card-body">
            <h3>{{ movie.title }}</h3>
            <p>{{ movie.subtitle }}</p>
            <router-link class="primary-link" :to="'/movies/' + movie.id">查閱場次</router-link>
          </div>
        </article>
      </section>
      <GlobalFloatingButtons />
    </div>
  `,
  components: {
    Hero,
    LoadingState,
    ErrorState,
    GlobalFloatingButtons
  },
  data() {
    return {
      movies: [],
      loading: true,
      error: null
    };
  },
  async mounted() {
    try {
      const response = await fetch('/api/movies');
      if (!response.ok) {
        throw new Error('無法載入電影列表。');
      }
      this.movies = await response.json();
      this.loading = false;
    } catch (err) {
      this.error = err.message || '發生未知錯誤。';
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
          <ErrorState :message="error || '找不到指定電影。'" />
          <button class="primary-link" @click="goBack">返回上一頁</button>
        </template>
        <template v-else>
          <button class="back-link" @click="goBack">返回</button>
          <div class="movie-detail-card">
            <img :src="movie.posterUrl" :alt="movie.title" />
            <div>
              <h2>{{ movie.title }}</h2>
              <p>{{ movie.description }}</p>
              <h3>場次資訊</h3>
              <ul class="showtime-list">
                <li v-for="showtime in movie.showtimes" :key="showtime.id" class="showtime-item">
                  <div class="showtime-meta">
                    <strong>{{ showtime.startTime }}</strong>
                    <span>{{ showtime.durationMinutes }} 分鐘</span>
                    <span>{{ showtime.auditorium }}</span>
                  </div>
                    <router-link class="primary-link"
                      :to="'/movies/' + movie.id + '/showtimes/' + showtime.id">
                      選擇座位
                    </router-link>
                </li>
              </ul>
            </div>
          </div>
        </template>
      </div>
    </div>
  `,
  components: {
    LoadingState,
    ErrorState
  },
  data() {
    return {
      movie: null,
      loading: true,
      error: null
    };
  },
  async mounted() {
    try {
      const response = await fetch(`/api/movies/${this.$route.params.movieId}`);
      if (!response.ok) {
        throw new Error('無法載入電影詳細資料。');
      }
      this.movie = await response.json();
      this.loading = false;
    } catch (err) {
      this.error = err.message || '讀取電影內容時發生錯誤。';
      this.loading = false;
    }
  },
  methods: {
    goBack() {
      this.$router.go(-1);
    }
  }
};

const SeatSelectionPage = {
  template: `
    <div class="app-shell">
      <div class="page">
        <LoadingState v-if="loading" />
        <template v-else-if="error || !movie || !details">
          <ErrorState :message="error || '無法取得座位資訊。'" />
          <button class="primary-link" @click="goBack">返回上一頁</button>
        </template>
        <template v-else>
          <button class="back-link" @click="goBack">返回</button>
          <div class="seat-selection">
            <div class="movie-detail-card">
              <img :src="movie.posterUrl" :alt="movie.title" />
              <div>
                <h2>{{ movie.title }}</h2>
                <p>{{ details.showtime.startTime }} · {{ details.showtime.auditorium }}</p>
                <p>{{ details.showtime.durationMinutes }} 分鐘</p>
              </div>
            </div>
            <div class="seat-grid" role="grid">
              <div v-for="(row, rowIndex) in seatRows" :key="rowIndex" class="seat-row">
                <span class="row-label">{{ String.fromCharCode(65 + rowIndex) }}</span>
                <div class="seat-row-grid">
                  <button
                    v-for="seat in row"
                    :key="seat.seatId"
                    type="button"
                    :class="getSeatClass(seat)"
                    @click="toggleSeat(seat.seatId, seat.reserved)"
                  >
                    {{ seat.seatId.substring(1) }}
                  </button>
                </div>
              </div>
            </div>
            <div class="selection-summary">
              <div>
                <strong>已選座位：</strong>
                {{ selectedSeats.length > 0 ? selectedSeats.join(', ') : '尚未選擇' }}
              </div>
              <button type="button" :disabled="selectedSeats.length === 0">
                確認訂票
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>
  `,
  components: {
    LoadingState,
    ErrorState
  },
  data() {
    return {
      movie: null,
      details: null,
      loading: true,
      error: null,
      selectedSeats: []
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
      try {
        const [movieRes, showtimeRes] = await Promise.all([
          fetch(`/api/movies/${this.$route.params.movieId}`),
          fetch(`/api/movies/${this.$route.params.movieId}/showtimes/${this.$route.params.showtimeId}`)
        ]);

        if (!movieRes.ok) {
          throw new Error('無法載入電影資訊。');
        }
        if (!showtimeRes.ok) {
          throw new Error('無法載入場次資料。');
        }

        this.movie = await movieRes.json();
        this.details = await showtimeRes.json();
        this.loading = false;
      } catch (err) {
        this.error = err.message || '讀取座位配置時發生錯誤。';
        this.loading = false;
      }
    },
    toggleSeat(seatId, reserved) {
      if (reserved) return;
      const index = this.selectedSeats.indexOf(seatId);
      if (index > -1) {
        this.selectedSeats.splice(index, 1);
      } else {
        this.selectedSeats.push(seatId);
      }
    },
    getSeatClass(seat) {
      const classes = ['seat'];
      if (seat.reserved) {
        classes.push('reserved');
      } else {
        classes.push('available');
      }
      if (this.selectedSeats.includes(seat.seatId)) {
        classes.push('selected');
      }
      return classes.join(' ');
    },
    goBack() {
      this.$router.go(-1);
    }
  }
};

const routes = [
  { path: '/', component: HomePage },
  { path: '/movies/:movieId', component: MovieDetailPage },
  { path: '/movies/:movieId/showtimes/:showtimeId', component: SeatSelectionPage },
  { path: '/:pathMatch(.*)*', redirect: '/' }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

const app = createApp({
  template: '<router-view></router-view>'
});

app.use(router);
app.mount('#root');
