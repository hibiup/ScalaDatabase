-- Destroy
DROP TABLE IF EXISTS Users;
DROP TABLE IF EXISTS Accounts;

-- Creation
CREATE TABLE Accounts(
  id INT AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  password VARCHAR(255),
  last_time TIMESTAMP AS CURRENT_TIMESTAMP,
  UNIQUE KEY EMAIL(EMAIL)
);

CREATE TABLE Users(
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  first_name VARCHAR(255),
  last_name varchar(255),
  foreign key (user_id) references Users(user_id)
);

-- Initial data set
INSERT INTO ACCOUNTS(EMAIL, PASSWORD) VALUES('sample@gmail.com','password');
INSERT INTO USERS(USER_ID, FIRST_NAME, LAST_NAME) VALUES(1, 'John','Smith');