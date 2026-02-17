-- Canonical movie asset mapping.
-- Keep frequently changing poster/carousel URLs in this repeatable migration
-- instead of adding new versioned V*.sql migrations for content-only updates.

UPDATE movie_catalog
SET poster_url = '/images/dune-part-two.jpg'
WHERE movie_id = 'mv-01';

UPDATE movie_catalog
SET poster_url = 'https://image.tmdb.org/t/p/original/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg'
WHERE movie_id = 'mv-02';

UPDATE movie_catalog
SET poster_url = 'https://image.tmdb.org/t/p/original/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg'
WHERE movie_id = 'mv-03';

UPDATE movie_catalog
SET poster_url = 'https://image.tmdb.org/t/p/original/r2J02Z2OpNTctfOSN1Ydgii51I3.jpg'
WHERE movie_id = 'mv-04';

UPDATE movie_catalog
SET poster_url = 'https://image.tmdb.org/t/p/original/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg'
WHERE movie_id = 'mv-05';

UPDATE movie_catalog
SET poster_url = 'https://image.tmdb.org/t/p/w500/NNxYkU70HPurnNCSiCjYAmacwm.jpg'
WHERE movie_id = 'mv-06';

UPDATE movie_catalog
SET poster_url = 'https://image.tmdb.org/t/p/w500/vZloFAK7NmvMGKE7VkF5UHaz0I.jpg'
WHERE movie_id = 'mv-07';

UPDATE movie_catalog
SET poster_url = 'https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg'
WHERE movie_id = 'mv-08';

UPDATE movie_catalog
SET poster_url = 'https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg'
WHERE movie_id = 'mv-09';

UPDATE movie_catalog
SET poster_url = 'https://image.tmdb.org/t/p/w500/ps2oKfhY6DL3alynlSqY97gHSsg.jpg'
WHERE movie_id = 'mv-10';

UPDATE movie_catalog
SET carousel_image_url = 'https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s5hj0PX.jpg'
WHERE movie_id = 'mv-01';

UPDATE movie_catalog
SET carousel_image_url = 'https://image.tmdb.org/t/p/original/neeNHeXjMF5fXoCJRsOmkNGC7q.jpg'
WHERE movie_id = 'mv-02';

UPDATE movie_catalog
SET carousel_image_url = 'https://image.tmdb.org/t/p/original/nGxUxi3PfXDRm7Vg95VBNgNM8yc.jpg'
WHERE movie_id = 'mv-03';

UPDATE movie_catalog
SET carousel_image_url = 'https://image.tmdb.org/t/p/original/nSwxDkxeYydsmrqSqgPcCSxaKJz.jpg'
WHERE movie_id = 'mv-04';

UPDATE movie_catalog
SET carousel_image_url = 'https://image.tmdb.org/t/p/original/mbYTRO33LJAgpCMrIn9ibiWHbMH.jpg'
WHERE movie_id = 'mv-05';

UPDATE movie_catalog
SET carousel_image_url = 'https://image.tmdb.org/t/p/original/TFTfzrkX8L7bAKUcch6qLmjpLu.jpg'
WHERE movie_id = 'mv-06';

UPDATE movie_catalog
SET carousel_image_url = 'https://image.tmdb.org/t/p/original/7I6VUdPj6tQECNHdviJkUHD2u89.jpg'
WHERE movie_id = 'mv-07';

UPDATE movie_catalog
SET carousel_image_url = 'https://image.tmdb.org/t/p/original/eUORREWq2ThkkxyiCESCu3sVdGg.jpg'
WHERE movie_id = 'mv-08';

UPDATE movie_catalog
SET carousel_image_url = 'https://image.tmdb.org/t/p/original/8rpDcsfLJypbO6vREc0547VKqEv.jpg'
WHERE movie_id = 'mv-09';

UPDATE movie_catalog
SET carousel_image_url = 'https://image.tmdb.org/t/p/original/1GU0jS5ZG5MhfdDrpj8s9XxbgJJ.jpg'
WHERE movie_id = 'mv-10';

-- Ensure NULL carousel images never leak to UI.
UPDATE movie_catalog
SET carousel_image_url = poster_url
WHERE carousel_image_url IS NULL;
