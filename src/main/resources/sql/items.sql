CREATE TABLE IF NOT EXISTS items ( 
    item_id INTEGER PRIMARY KEY AUTO_INCREMENT,
    item_name VARCHAR(255) NOT NULL UNIQUE,
    base_price DECIMAL(10, 2) NOT NULL,
    min_price DECIMAL(10, 2) NOT NULL,
    sold_amount INTEGER DEFAULT 0,
    bought_amount INTEGER DEFAULT 0
);