-- Destroy
DROP TABLE users;

-- Creation
CREATE TABLE users
  (
  id INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY(START WITH 1, INCREMENT BY 1),
  email VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,
  lasttime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT email UNIQUE (email),
  CONSTRAINT primary_key PRIMARY KEY (id)
  );

-- Initial data set
INSERT INTO users(email, password) VALUES('sample@gmail.com','password');