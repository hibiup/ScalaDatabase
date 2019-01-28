-- Destroy
DROP TABLE IF EXISTS Users;
DROP TABLE IF EXISTS Accounts;

-- Creation
CREATE TABLE Users(
  id INT AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(255) NOT NULL,
  last_name varchar(255),
  register_date TIMESTAMP AS CURRENT_TIMESTAMP
);

CREATE TABLE Accounts(
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  email VARCHAR(255) NOT NULL,
  password VARCHAR(255),
  last_time TIMESTAMP AS CURRENT_TIMESTAMP,
  UNIQUE KEY EMAIL(EMAIL),
  foreign key (user_id) references Users(id)
);

-- Initial data set
INSERT INTO USERS(FIRST_NAME, LAST_NAME) VALUES('John','Smith');
INSERT INTO ACCOUNTS(USER_ID, EMAIL, PASSWORD) VALUES(1, 'sample@yahoo.com','$2a$10$ZHRv69WbouUkknTehO0dvOGPHeQ0nk0bmW.5qWiOhB3dc8gtw.UE6');
INSERT INTO ACCOUNTS(USER_ID, EMAIL, PASSWORD) VALUES(1, 'sample@gmail.com','$2a$10$ZHRv69WbouUkknTehO0dvOGPHeQ0nk0bmW.5qWiOhB3dc8gtw.UE6');
INSERT INTO ACCOUNTS(USER_ID, EMAIL) VALUES(1, 'sample@dummy.com');
