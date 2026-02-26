SET @travel_review_itinerary_col_exists := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'travel_review'
      AND column_name = 'travel_itinerary_id'
);

SET @travel_review_itinerary_col_ddl := IF(
    @travel_review_itinerary_col_exists = 0,
    'ALTER TABLE travel_review ADD COLUMN travel_itinerary_id BIGINT NULL',
    'SELECT 1'
);

PREPARE stmt FROM @travel_review_itinerary_col_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
