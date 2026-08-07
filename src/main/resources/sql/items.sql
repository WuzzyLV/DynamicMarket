CREATE TABLE IF NOT EXISTS items (
    item_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    item_name VARCHAR(255) NOT NULL UNIQUE,
    base_price DECIMAL(10, 2) NOT NULL,
    impact_k DECIMAL(20, 18) NOT NULL,
    min_price DECIMAL(10, 2) NOT NULL,
    category VARCHAR(64) NOT NULL DEFAULT 'misc',
    sold_amount BIGINT DEFAULT 0,
    bought_amount BIGINT DEFAULT 0,
    net_position DECIMAL(20, 6) NOT NULL DEFAULT 0,
    last_decay BIGINT NOT NULL DEFAULT 0,
    half_life_hours DECIMAL(10, 2) NOT NULL DEFAULT 48
);