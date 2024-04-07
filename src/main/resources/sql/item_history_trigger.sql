CREATE TRIGGER IF NOT EXISTS update_item_history AFTER UPDATE ON items
    FOR EACH ROW
BEGIN
    IF (NEW.bought_amount <> OLD.bought_amount OR NEW.sold_amount <> OLD.sold_amount) THEN
        INSERT INTO item_history (item_id, bought_amount, sold_amount)
        VALUES (NEW.item_id, NEW.bought_amount, NEW.sold_amount);
    END IF;
END;
