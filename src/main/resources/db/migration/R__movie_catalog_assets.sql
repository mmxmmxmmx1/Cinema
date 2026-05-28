-- Canonical movie asset mapping.
-- Keep frequently changing poster/carousel URLs in this repeatable migration
-- instead of adding new versioned V*.sql migrations for content-only updates.

UPDATE movie_catalog
SET poster_url = '/images/貓砂(1).png'
WHERE movie_id = 'mv-01';

UPDATE movie_catalog
SET poster_url = '/images/貓本海默(2).png'
WHERE movie_id = 'mv-02';

UPDATE movie_catalog
SET poster_url = '/images/狗狗人(1).png'
WHERE movie_id = 'mv-03';

UPDATE movie_catalog
SET poster_url = '/images/星際貓攻隊3(1).png'
WHERE movie_id = 'mv-04';

UPDATE movie_catalog
SET poster_url = '/images/狗比(1).png'
WHERE movie_id = 'mv-05';

UPDATE movie_catalog
SET poster_url = '/images/不可能的任務-貓咪神算(1).png'
WHERE movie_id = 'mv-06';

UPDATE movie_catalog
SET poster_url = '/images/捍衛狗狗4(2_3).png'
WHERE movie_id = 'mv-07';

UPDATE movie_catalog
SET poster_url = '/images/貓貓俠(1).png'
WHERE movie_id = 'mv-08';

UPDATE movie_catalog
SET poster_url = '/images/阿凡狗_誰之道(1).png'
WHERE movie_id = 'mv-09';

UPDATE movie_catalog
SET poster_url = '/images/黑貓-烏干達萬歲(1).png'
WHERE movie_id = 'mv-10';

UPDATE movie_catalog
SET carousel_image_url = '/images/貓砂(2).png'
WHERE movie_id = 'mv-01';

UPDATE movie_catalog
SET carousel_image_url = '/images/貓本海默(1).png'
WHERE movie_id = 'mv-02';

UPDATE movie_catalog
SET carousel_image_url = '/images/狗狗人(2).png'
WHERE movie_id = 'mv-03';

UPDATE movie_catalog
SET carousel_image_url = '/images/星際貓攻隊3(2).png'
WHERE movie_id = 'mv-04';

UPDATE movie_catalog
SET carousel_image_url = '/images/狗比(2).png'
WHERE movie_id = 'mv-05';

UPDATE movie_catalog
SET carousel_image_url = '/images/不可能的任務-貓咪神算(2).png'
WHERE movie_id = 'mv-06';

UPDATE movie_catalog
SET carousel_image_url = '/images/捍衛狗狗4(16_9).png'
WHERE movie_id = 'mv-07';

UPDATE movie_catalog
SET carousel_image_url = '/images/貓貓俠(2).png'
WHERE movie_id = 'mv-08';

UPDATE movie_catalog
SET carousel_image_url = '/images/阿凡狗_誰之道(2).png'
WHERE movie_id = 'mv-09';

UPDATE movie_catalog
SET carousel_image_url = '/images/黑貓-烏干達萬歲(2).png'
WHERE movie_id = 'mv-10';

-- Ensure NULL carousel images never leak to UI.
UPDATE movie_catalog
SET carousel_image_url = poster_url
WHERE carousel_image_url IS NULL;
