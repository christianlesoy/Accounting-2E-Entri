CREATE DATABASE IF NOT EXISTS entri_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE entri_db;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_settings (
    user_id BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'USD',
    PRIMARY KEY (user_id),
    CONSTRAINT chk_user_settings_currency CHECK (currency_code IN ('PHP','USD')),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ledger_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    account_code VARCHAR(20) NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    account_type ENUM('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ledger_user_code (user_id, account_code),
    UNIQUE KEY uq_ledger_user_name (user_id, account_name),
    KEY idx_ledger_user_type (user_id, account_type),
    CONSTRAINT fk_ledger_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS journal_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    entry_date DATE NOT NULL,
    particulars VARCHAR(500) NOT NULL,
    currency CHAR(3) NOT NULL,
    entry_type ENUM('STANDARD','ADJUSTMENT') NOT NULL DEFAULT 'STANDARD',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_journal_user_date (user_id, entry_date, id),
    CONSTRAINT fk_journal_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS journal_entry_lines (
    id BIGINT NOT NULL AUTO_INCREMENT,
    journal_entry_id BIGINT NOT NULL,
    ledger_account_id BIGINT NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    debit DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    credit DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    PRIMARY KEY (id),
    KEY idx_journal_lines_entry (journal_entry_id),
    KEY idx_journal_lines_account (ledger_account_id),
    CONSTRAINT chk_journal_line_amounts CHECK (
        (debit > 0 AND credit = 0) OR (debit = 0 AND credit > 0)
    ),
    CONSTRAINT fk_journal_line_entry FOREIGN KEY (journal_entry_id)
        REFERENCES journal_entries(id) ON DELETE CASCADE,
    CONSTRAINT fk_journal_line_account FOREIGN KEY (ledger_account_id)
        REFERENCES ledger_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS recent_views (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    item_type ENUM('JOURNAL_ENTRY','LEDGER_ACCOUNT','WORKSHEET') NOT NULL,
    item_id BIGINT NULL,
    item_name VARCHAR(200) NOT NULL,
    viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_recent_user_viewed (user_id, viewed_at),
    CONSTRAINT fk_recent_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Run separately as a MySQL administrator. Replace the placeholder locally.
-- CREATE USER 'entri_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_A_LONG_RANDOM_PASSWORD';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON entri_db.* TO 'entri_app'@'localhost';
-- FLUSH PRIVILEGES;
