UPDATE app_user
SET password_hash = '$2a$10$Iynov8ShQiS0o8BuzlIn1uEJVjjA1iBD9vqHu9Ht..kTNFliRNUSm'
WHERE username = 'admin'
  AND role = 'ADMIN'
  AND password_hash = '$2y$10$g10ppELimqMPmCQzalBPa.x0GbwCZdDWR0M6QVPr/0ybxWFxX5q1G';
