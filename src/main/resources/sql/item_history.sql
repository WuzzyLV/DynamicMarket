CREATE TABLE IF NOT EXISTS item_history (
      history_id INT PRIMARY KEY AUTO_INCREMENT,
      item_id INT NOT NULL,
      bought_amount BIGINT,
      sold_amount BIGINT,
      net_position DECIMAL(20, 6) NOT NULL DEFAULT 0,
      unit_price DECIMAL(20, 6) NOT NULL DEFAULT 0,
      change_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (item_id) REFERENCES items(item_id),
      INDEX idx_item_time (item_id, change_date),
      INDEX idx_time (change_date)
);