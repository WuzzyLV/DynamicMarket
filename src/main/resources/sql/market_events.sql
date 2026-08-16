CREATE TABLE IF NOT EXISTS market_events (
    event_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    definition_id VARCHAR(64) NOT NULL,
    scope VARCHAR(16) NOT NULL,
    target VARCHAR(64),
    direction VARCHAR(8) NOT NULL,
    multiplier DECIMAL(10, 4) NOT NULL,
    triggered_at BIGINT NOT NULL,
    INDEX idx_triggered_at (triggered_at)
);
