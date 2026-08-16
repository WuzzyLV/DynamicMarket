CREATE TABLE IF NOT EXISTS market_event_items (
    event_id BIGINT NOT NULL,
    item_name VARCHAR(64) NOT NULL,
    applied_delta DECIMAL(20, 6) NOT NULL,
    FOREIGN KEY (event_id) REFERENCES market_events(event_id) ON DELETE CASCADE,
    INDEX idx_event (event_id),
    INDEX idx_item (item_name)
);
