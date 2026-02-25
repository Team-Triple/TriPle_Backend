CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(255),
    provider_id VARCHAR(255),
    nickname VARCHAR(255),
    email VARCHAR(255),
    gender VARCHAR(255),
    birth DATE,
    description VARCHAR(255),
    profile_url VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS travel_group (
    group_id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT,
    group_kind VARCHAR(255),
    name VARCHAR(255),
    description VARCHAR(255),
    thumb_nail_url VARCHAR(255),
    current_member_count INTEGER NOT NULL,
    member_limit INTEGER NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (group_id)
);

CREATE TABLE IF NOT EXISTS user_group (
    user_group_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    group_id BIGINT,
    role VARCHAR(255),
    join_status VARCHAR(255),
    joined_at DATETIME(6),
    left_at DATETIME(6),
    PRIMARY KEY (user_group_id)
);

CREATE TABLE IF NOT EXISTS join_apply (
    join_apply_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    group_id BIGINT,
    join_apply_status VARCHAR(255),
    approved_at DATETIME(6),
    rejected_at DATETIME(6),
    canceled_at DATETIME(6),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (join_apply_id),
    UNIQUE KEY uk_join_apply_group_user (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS file (
    file_id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT,
    file_key VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (file_id)
);

CREATE TABLE IF NOT EXISTS travel_itinerary (
    travel_itinerary_id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255),
    start_at DATETIME(6),
    end_at DATETIME(6),
    group_id BIGINT,
    description VARCHAR(255),
    thumbnail_url VARCHAR(255),
    member_limit INTEGER NOT NULL,
    member_count INTEGER NOT NULL,
    is_deleted BIT NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (travel_itinerary_id),
    UNIQUE KEY uk_travel_itinerary_group (group_id)
);

CREATE TABLE IF NOT EXISTS user_travel_itinerary (
    user_travel_itinerary_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    travel_itinerary_id BIGINT,
    user_role INTEGER,
    PRIMARY KEY (user_travel_itinerary_id)
);

CREATE TABLE IF NOT EXISTS travel_review (
    travel_review_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    content VARCHAR(255),
    is_deleted BIT NOT NULL,
    view INTEGER NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (travel_review_id)
);

CREATE TABLE IF NOT EXISTS travel_review_image (
    travel_review_image_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    travel_review_id BIGINT,
    review_image_url VARCHAR(255),
    PRIMARY KEY (travel_review_image_id)
);

CREATE TABLE IF NOT EXISTS invoice (
    invoice_id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT,
    creator_user_id BIGINT,
    travel_itinerary_id BIGINT,
    invoice_status INTEGER,
    title VARCHAR(255),
    total_amount DECIMAL(38,2),
    due_at DATETIME(6),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (invoice_id),
    UNIQUE KEY uk_invoice_travel_itinerary (travel_itinerary_id)
);

CREATE TABLE IF NOT EXISTS invoice_user (
    invoice_user_id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT,
    user_id BIGINT,
    remain_amount DECIMAL(38,2) NOT NULL,
    PRIMARY KEY (invoice_user_id)
);

CREATE TABLE IF NOT EXISTS payment (
    payment_id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT,
    pg_provider VARCHAR(255),
    method VARCHAR(255),
    order_id VARCHAR(255),
    payment_key VARCHAR(255),
    requested_amount DECIMAL(38,2),
    approved_amount DECIMAL(38,2),
    payment_status VARCHAR(255),
    approved_at DATETIME(6),
    requested_at DATETIME(6),
    receipt_url VARCHAR(255),
    failure_code VARCHAR(255),
    failure_message VARCHAR(255),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (payment_id)
);

-- Add FULLTEXT index for group keyword search on name + description.
SET @fulltext_idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'travel_group'
      AND index_name = 'idx_travel_group_name_description'
);

SET @fulltext_ddl := IF(
    @fulltext_idx_exists = 0,
    'ALTER TABLE travel_group ADD FULLTEXT INDEX idx_travel_group_name_description (name, description)',
    'SELECT 1'
);

PREPARE stmt FROM @fulltext_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @user_group_idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user_group'
      AND index_name = 'uk_user_group_group_user'
);

SET @user_group_ddl := IF(
    @user_group_idx_exists = 0,
    'ALTER TABLE user_group ADD CONSTRAINT uk_user_group_group_user UNIQUE (group_id, user_id)',
    'SELECT 1'
);

PREPARE stmt FROM @user_group_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
