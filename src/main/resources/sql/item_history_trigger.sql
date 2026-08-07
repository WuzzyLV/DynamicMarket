CREATE TRIGGER update_item_history AFTER UPDATE ON items
    FOR EACH ROW
BEGIN
    IF (NEW.bought_amount <> OLD.bought_amount OR NEW.sold_amount <> OLD.sold_amount) THEN
        INSERT INTO item_history (item_id, bought_amount, sold_amount, net_position, unit_price)
        VALUES (NEW.item_id, NEW.bought_amount, NEW.sold_amount, NEW.net_position,
                NEW.base_price * EXP(NEW.impact_k * NEW.net_position));
    END IF;
END;
