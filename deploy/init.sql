CREATE DATABASE IF NOT EXISTS skillbridge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'skillbridge'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON skillbridge.* TO 'skillbridge'@'localhost';
FLUSH PRIVILEGES;
