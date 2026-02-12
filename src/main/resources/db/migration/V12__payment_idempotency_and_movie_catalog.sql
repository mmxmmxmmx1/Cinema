CREATE TABLE IF NOT EXISTS payment_idempotency (
  id BIGINT NOT NULL AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_idempotency (member_id, order_id, idempotency_key),
  KEY idx_payment_idempotency_updated (updated_at),
  CONSTRAINT fk_payment_idempotency_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
  CONSTRAINT fk_payment_idempotency_order FOREIGN KEY (order_id) REFERENCES member_orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS movie_catalog (
  movie_id VARCHAR(50) NOT NULL,
  title VARCHAR(120) NOT NULL,
  subtitle VARCHAR(120) NULL,
  poster_url VARCHAR(500) NULL,
  description VARCHAR(1000) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (movie_id),
  KEY idx_movie_catalog_enabled_order (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS movie_showtimes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  movie_id VARCHAR(50) NOT NULL,
  showtime_id VARCHAR(50) NOT NULL,
  start_time VARCHAR(5) NOT NULL,
  duration_minutes INT NOT NULL,
  auditorium VARCHAR(100) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_movie_showtimes_movie_showtime (movie_id, showtime_id),
  KEY idx_movie_showtimes_movie_enabled_order (movie_id, enabled, sort_order),
  CONSTRAINT fk_movie_showtimes_movie FOREIGN KEY (movie_id) REFERENCES movie_catalog (movie_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO movie_catalog (movie_id, title, subtitle, poster_url, description, enabled, sort_order) VALUES
  ('mv-01', '沙丘:第二部', '', '/images/dune-part-two.jpg', '保羅亞崔迪與錢妮以及弗瑞曼人聯手,向毀滅他家族的陰謀者展開報復。', 1, 10),
  ('mv-02', '奧本海默', '', '/images/cinema1.png', '羅伯特奧本海默的一生,從他在原子彈研發中的角色,到他面臨的道德困境。', 1, 20),
  ('mv-03', '蜘蛛人:穿越新宇宙', '', '/images/spider-verse.jpg', '邁爾斯摩拉斯再次穿越多重宇宙,與其他蜘蛛人並肩作戰。', 1, 30),
  ('mv-04', '星際異攻隊3', '', 'https://image.tmdb.org/t/p/w500/r2J02Z2OpNTctfOSN1Ydgii51I3.jpg', '星爵與他的團隊踏上一場全新的冒險,面對來自宇宙的威脅。', 1, 40),
  ('mv-05', '芭比', '', 'https://image.tmdb.org/t/p/w500/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg', '芭比與肯踏上現實世界的旅程,發現真實生活的美好與挑戰。', 1, 50),
  ('mv-06', '不可能的任務:致命清算', '', 'https://image.tmdb.org/t/p/w500/NNxYkU70HPurnNCSiCjYAmacwm.jpg', 'IMF探員伊森韓特面對他職業生涯中最致命的任務。', 1, 60),
  ('mv-07', '捍衛任務4', '', 'https://image.tmdb.org/t/p/w500/vZloFAK7NmvMGKE7VkF5UHaz0I.jpg', '約翰維克尋找擊敗高桌會的方法,以贏回他的自由。', 1, 70),
  ('mv-08', '蝙蝠俠', '', 'https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg', '布魯斯韋恩在高譚市擔任蝙蝠俠的第二年,追蹤一名殘忍的連環殺手。', 1, 80),
  ('mv-09', '阿凡達:水之道', '', 'https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg', '傑克蘇里與娜蒂莉在潘朵拉星球建立家庭,面對新的威脅。', 1, 90),
  ('mv-10', '黑豹2:瓦干達萬歲', '', 'https://image.tmdb.org/t/p/w500/ps2oKfhY6DL3alynlSqY97gHSsg.jpg', '瓦干達王國的領袖們為了保護國家,與強大的海底王國對抗。', 1, 100)
AS incoming
ON DUPLICATE KEY UPDATE
  title = incoming.title,
  subtitle = incoming.subtitle,
  poster_url = incoming.poster_url,
  description = incoming.description,
  enabled = incoming.enabled,
  sort_order = incoming.sort_order;

INSERT INTO movie_showtimes (movie_id, showtime_id, start_time, duration_minutes, auditorium, enabled, sort_order) VALUES
  ('mv-01', 'mv-01-st1', '09:30', 166, '1號廳', 1, 10),
  ('mv-01', 'mv-01-st2', '12:15', 166, '2號廳', 1, 20),
  ('mv-01', 'mv-01-st3', '15:00', 166, '3號廳', 1, 30),
  ('mv-01', 'mv-01-st4', '18:40', 166, '1號廳', 1, 40),
  ('mv-01', 'mv-01-st5', '21:25', 166, '2號廳', 1, 50),
  ('mv-01', 'mv-01-st6', '23:00', 166, '3號廳', 1, 60),

  ('mv-02', 'mv-02-st1', '08:50', 180, '1號廳', 1, 10),
  ('mv-02', 'mv-02-st2', '11:45', 180, '2號廳', 1, 20),
  ('mv-02', 'mv-02-st3', '14:30', 180, '3號廳', 1, 30),
  ('mv-02', 'mv-02-st4', '17:30', 180, '1號廳', 1, 40),
  ('mv-02', 'mv-02-st5', '20:40', 180, '2號廳', 1, 50),
  ('mv-02', 'mv-02-st6', '23:00', 180, '3號廳', 1, 60),

  ('mv-03', 'mv-03-st1', '10:20', 140, '1號廳', 1, 10),
  ('mv-03', 'mv-03-st2', '12:40', 140, '2號廳', 1, 20),
  ('mv-03', 'mv-03-st3', '15:20', 140, '3號廳', 1, 30),
  ('mv-03', 'mv-03-st4', '18:05', 140, '1號廳', 1, 40),
  ('mv-03', 'mv-03-st5', '21:10', 140, '2號廳', 1, 50),
  ('mv-03', 'mv-03-st6', '23:00', 140, '3號廳', 1, 60),

  ('mv-04', 'mv-04-st1', '09:10', 150, '1號廳', 1, 10),
  ('mv-04', 'mv-04-st2', '12:10', 150, '2號廳', 1, 20),
  ('mv-04', 'mv-04-st3', '15:10', 150, '3號廳', 1, 30),
  ('mv-04', 'mv-04-st4', '18:10', 150, '1號廳', 1, 40),
  ('mv-04', 'mv-04-st5', '21:15', 150, '2號廳', 1, 50),
  ('mv-04', 'mv-04-st6', '23:00', 150, '3號廳', 1, 60),

  ('mv-05', 'mv-05-st1', '11:00', 114, '1號廳', 1, 10),
  ('mv-05', 'mv-05-st2', '13:20', 114, '2號廳', 1, 20),
  ('mv-05', 'mv-05-st3', '15:40', 114, '3號廳', 1, 30),
  ('mv-05', 'mv-05-st4', '18:00', 114, '1號廳', 1, 40),
  ('mv-05', 'mv-05-st5', '20:30', 114, '2號廳', 1, 50),
  ('mv-05', 'mv-05-st6', '23:00', 114, '3號廳', 1, 60),

  ('mv-06', 'mv-06-st1', '08:30', 163, '1號廳', 1, 10),
  ('mv-06', 'mv-06-st2', '11:45', 163, '2號廳', 1, 20),
  ('mv-06', 'mv-06-st3', '15:00', 163, '3號廳', 1, 30),
  ('mv-06', 'mv-06-st4', '18:10', 163, '1號廳', 1, 40),
  ('mv-06', 'mv-06-st5', '21:25', 163, '2號廳', 1, 50),
  ('mv-06', 'mv-06-st6', '23:00', 163, '3號廳', 1, 60),

  ('mv-07', 'mv-07-st1', '11:20', 169, '1號廳', 1, 10),
  ('mv-07', 'mv-07-st2', '14:10', 169, '2號廳', 1, 20),
  ('mv-07', 'mv-07-st3', '17:00', 169, '3號廳', 1, 30),
  ('mv-07', 'mv-07-st4', '19:50', 169, '1號廳', 1, 40),
  ('mv-07', 'mv-07-st5', '22:40', 169, '2號廳', 1, 50),

  ('mv-08', 'mv-08-st1', '09:00', 176, '1號廳', 1, 10),
  ('mv-08', 'mv-08-st2', '12:20', 176, '2號廳', 1, 20),
  ('mv-08', 'mv-08-st3', '15:40', 176, '3號廳', 1, 30),
  ('mv-08', 'mv-08-st4', '19:00', 176, '1號廳', 1, 40),
  ('mv-08', 'mv-08-st5', '22:20', 176, '2號廳', 1, 50),
  ('mv-08', 'mv-08-st6', '23:00', 176, '3號廳', 1, 60),

  ('mv-09', 'mv-09-st1', '10:00', 192, '1號廳', 1, 10),
  ('mv-09', 'mv-09-st2', '13:20', 192, '2號廳', 1, 20),
  ('mv-09', 'mv-09-st3', '16:40', 192, '3號廳', 1, 30),
  ('mv-09', 'mv-09-st4', '20:00', 192, '1號廳', 1, 40),
  ('mv-09', 'mv-09-st5', '23:20', 192, '2號廳', 1, 50),

  ('mv-10', 'mv-10-st1', '09:10', 161, '1號廳', 1, 10),
  ('mv-10', 'mv-10-st2', '12:20', 161, '2號廳', 1, 20),
  ('mv-10', 'mv-10-st3', '15:30', 161, '3號廳', 1, 30),
  ('mv-10', 'mv-10-st4', '18:40', 161, '1號廳', 1, 40),
  ('mv-10', 'mv-10-st5', '21:50', 161, '2號廳', 1, 50),
  ('mv-10', 'mv-10-st6', '23:00', 161, '3號廳', 1, 60)
AS incoming
ON DUPLICATE KEY UPDATE
  start_time = incoming.start_time,
  duration_minutes = incoming.duration_minutes,
  auditorium = incoming.auditorium,
  enabled = incoming.enabled,
  sort_order = incoming.sort_order;

UPDATE member_tickets mt
LEFT JOIN showtime_overrides so
  ON so.movie_id = mt.movie_id
  AND so.showtime_id = mt.showtime_id
  AND so.enabled = 1
LEFT JOIN movie_showtimes ms
  ON ms.movie_id = mt.movie_id
  AND ms.showtime_id = mt.showtime_id
  AND ms.enabled = 1
SET mt.show_start_at = TIMESTAMP(mt.show_date, COALESCE(so.start_time, ms.start_time))
WHERE TIME(mt.show_start_at) = '00:00:00'
  AND COALESCE(so.start_time, ms.start_time) IS NOT NULL;
