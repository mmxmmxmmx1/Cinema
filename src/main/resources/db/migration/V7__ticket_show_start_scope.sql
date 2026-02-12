ALTER TABLE member_tickets
  ADD COLUMN show_start_at DATETIME NULL AFTER show_date;

UPDATE member_tickets
SET show_start_at = TIMESTAMP(show_date, '00:00:00')
WHERE show_start_at IS NULL;

ALTER TABLE member_tickets
  MODIFY COLUMN show_start_at DATETIME NOT NULL;

ALTER TABLE member_tickets
  DROP INDEX uk_member_tickets_showdate_showtime_seat;

ALTER TABLE member_tickets
  ADD UNIQUE KEY uk_member_tickets_showstart_showtime_seat (show_start_at, showtime_id, seat_id),
  ADD KEY idx_member_tickets_showstart_showtime (show_start_at, showtime_id);
