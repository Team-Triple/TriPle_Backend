SET @invoice_unique_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'invoice'
      AND index_name = 'uk_invoice_travel_itinerary'
);

SET @invoice_unique_ddl := IF(
    @invoice_unique_exists = 0,
    'ALTER TABLE invoice ADD CONSTRAINT uk_invoice_travel_itinerary UNIQUE (travel_itinerary_id)',
    'SELECT 1'
);

PREPARE stmt FROM @invoice_unique_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @invoice_user_unique_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'invoice_user'
      AND index_name = 'uk_invoice_user_invoice_user'
);

SET @invoice_user_unique_ddl := IF(
    @invoice_user_unique_exists = 0,
    'ALTER TABLE invoice_user ADD CONSTRAINT uk_invoice_user_invoice_user UNIQUE (invoice_id, user_id)',
    'SELECT 1'
);

PREPARE stmt FROM @invoice_user_unique_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
