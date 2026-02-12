ALTER TABLE member_tickets
  ADD COLUMN show_date DATE NULL AFTER showtime_id;

UPDATE member_tickets
SET show_date = DATE(DATE_SUB(purchased_at, INTERVAL 5 HOUR))
WHERE show_date IS NULL;

ALTER TABLE member_tickets
  MODIFY COLUMN show_date DATE NOT NULL;

ALTER TABLE member_tickets
  DROP INDEX uk_member_tickets_showtime_seat;

ALTER TABLE member_tickets
  ADD UNIQUE KEY uk_member_tickets_showdate_showtime_seat (show_date, showtime_id, seat_id),
  ADD KEY idx_member_tickets_showdate_showtime (show_date, showtime_id);
