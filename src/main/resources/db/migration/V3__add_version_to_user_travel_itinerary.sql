SET @user_travel_itinerary_version_col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_travel_itinerary'
      AND column_name = 'version'
);

SET @user_travel_itinerary_version_col_ddl := IF(
    @user_travel_itinerary_version_col_exists = 0,
    'ALTER TABLE user_travel_itinerary ADD COLUMN version BIGINT NULL',
    'SELECT 1'
);

PREPARE stmt FROM @user_travel_itinerary_version_col_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
