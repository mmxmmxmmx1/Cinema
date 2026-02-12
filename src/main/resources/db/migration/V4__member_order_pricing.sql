ALTER TABLE member_orders
  ADD COLUMN unit_price INT NOT NULL DEFAULT 300,
  ADD COLUMN total_price INT NOT NULL DEFAULT 0;

UPDATE member_orders
SET total_price = total_qty * unit_price
WHERE total_price = 0;

