UPDATE app_user
SET password_hash = '$2y$12$pD3HE0BRYXxqqQYc5O8/8eIBKAp074oS7m238p9ZEmICyYK0PLcnG',
    enabled = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin'
  AND password_hash IN (
      '$2y$10$g10ppELimqMPmCQzalBPa.x0GbwCZdDWR0M6QVPr/0ybxWFxX5q1G',
      '$2a$10$Iynov8ShQiS0o8BuzlIn1uEJVjjA1iBD9vqHu9Ht..kTNFliRNUSm'
  );
