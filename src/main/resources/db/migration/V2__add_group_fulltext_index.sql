-- Add FULLTEXT index for group keyword search on name + description.
SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'travel_group'
      AND index_name = 'idx_travel_group_name_description'
);

SET @ddl := IF(
    @idx_exists = 0,
    'ALTER TABLE travel_group ADD FULLTEXT INDEX idx_travel_group_name_description (name, description)',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
