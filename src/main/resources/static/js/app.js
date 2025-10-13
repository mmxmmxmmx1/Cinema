const { createApp } = Vue;
const { createRouter, createWebHistory } = VueRouter;

// 載入中組件
const LoadingState = {
  template: '<div class="loading">載入中...</div>'
};

// 錯誤狀態組件
const ErrorState = {
  template: '<div class="error-state">{{ message }}</div>',
  props: ['message']
};

// Hero 標題組件
const Hero = {
  template: `
    <header class="hero">
      <span class="hero-label">現正熱映中</span>
      <img class="hero-badge" src="/images/sleep.jpg" alt="Very Sleepy Cinema 徽章">
      <div class="hero-text">
        <h1>很好睡電影院</h1>
        <p>每個顧客都可以睡得很安穩</p>
      </div>
    </header>
  `
};

// 浮動按鈕組件
const GlobalFloatingButtons = {
  template: `
    <div>
      <a class="floating-button login" href="/member/login">會員登入</a>
      <a class="floating-button employee" href="/employee/login">員工專區</a>
    </div>
  `
};

// 首頁組件 - 電影列表
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
            <router-link class="primary-link" :to="'/movies/' + movie.id">查看場次</router-link>
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
      this.error = err.message;
      this.loading = false;
    }
  }
};

// 電影詳情頁組件
const MovieDetailPage = {
  template: `
    <div class="app-shell">
      <div class="page">
        <LoadingState v-if="loading" />
        <template v-else-if="error || !movie">
          <ErrorState :message="error || '找不到電影資料。'" />
          <button class="primary-link" @click="goBack">返回上一頁</button>
        </template>
        <template v-else>
          <button class="back-link" @click="goBack">← 返回</button>
          <div class="movie-detail-card">
            <img :src="movie.posterUrl" :alt="movie.title" />
            <div>
              <h2>{{ movie.title }}</h2>
              <p>{{ movie.description }}</p>
              <h3>查看場次</h3>
              <ul class="showtime-list">
                <li v-for="showtime in movie.showtimes" :key="showtime.id" class="showtime-item">
                  <div class="showtime-meta">
                    <strong>{{ showtime.startTime }}</strong>
                    <span>{{ showtime.durationMinutes }} 分鐘</span>
                    <span>{{ showtime.auditorium }}</span>
                  </div>
                  <router-link class="primary-link" :to="'/movies/' + movie.id + '/showtimes/' + showtime.id">
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
    await this.loadMovie();
  },
  watch: {
    '$route.params.movieId': {
      handler() {
        this.loadMovie();
      }
    }
  },
  methods: {
    async loadMovie() {
      this.loading = true;
      this.error = null;
      try {
        const response = await fetch(`/api/movies/${this.$route.params.movieId}`);
        if (!response.ok) {
          throw new Error('找不到電影。');
        }
        this.movie = await response.json();
        this.loading = false;
      } catch (err) {
        this.error = err.message;
        this.loading = false;
      }
    },
    goBack() {
      this.$router.go(-1);
    }
  }
};

// 座位選擇頁組件
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
          <button class="back-link" @click="goBack">← 返回</button>
          <div class="seat-layout">
            <div>
              <h2>{{ movie.title }}</h2>
              <p>{{ details.showtime.startTime }} | {{ details.showtime.durationMinutes }} 分鐘 | {{ details.showtime.auditorium }}</p>
            </div>
            <div class="seat-legend">
              <span><span class="legend-box legend-available"></span> 可選座位</span>
              <span><span class="legend-box legend-selected"></span> 已選座位</span>
              <span><span class="legend-box legend-reserved"></span> 已預留座位</span>
            </div>
            <div class="seat-grid">
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
                <strong>已選座位：</strong> {{ selectedSeats.length > 0 ? selectedSeats.join(', ') : '尚未選擇' }}
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
          throw new Error('找不到電影。');
        }
        if (!showtimeRes.ok) {
          throw new Error('找不到場次資訊。');
        }

        this.movie = await movieRes.json();
        this.details = await showtimeRes.json();
        this.loading = false;
      } catch (err) {
        this.error = err.message;
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

// 路由配置
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

// 建立並掛載 Vue 應用程式
const app = createApp({
  template: '<router-view></router-view>'
});

app.use(router);
app.mount('#root');
