-- Destroy
DROP TABLE IF EXISTS users ;

-- Creation
CREATE TABLE users(
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  password VARCHAR(255),
  lasttime TIMESTAMP AS CURRENT_TIMESTAMP,
  UNIQUE KEY email(email)
);

-- Initial data set
INSERT INTO users(email, password) VALUES('sample@gmail.com','password');