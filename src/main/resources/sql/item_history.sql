CREATE TABLE IF NOT EXISTS item_history (
      history_id INT PRIMARY KEY AUTO_INCREMENT,
      item_id INT NOT NULL,
      bought_amount INT,
      sold_amount INT,
      change_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (item_id) REFERENCES items(item_id)
);
