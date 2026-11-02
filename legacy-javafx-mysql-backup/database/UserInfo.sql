ALTER USER 'entri_app'@'localhost'
  IDENTIFIED BY 'desisnotdes4165397';

GRANT SELECT, INSERT, UPDATE, DELETE
  ON entri_db.* TO 'entri_app'@'localhost';

FLUSH PRIVILEGES;