DELIMITER //

create trigger delete_signups_trigger
    before delete
    on events
    for each row
begin
    delete
    from event_signups
    where event_id = OLD.id;
end//


create trigger update_signups_trigger
    before UPDATE
    ON events
    FOR EACH ROW
begin
    if ((OLD.sign_up is true and NEW.sign_up is false)
        or (OLD.sign_up_form is not null and NEW.sign_up_form is null)
        or (OLD.sign_up_form is null and NEW.sign_up_form is not null)
        or not OLD.sign_up_form = NEW.sign_up_form) then
        delete
        from event_signups
        where event_id = OLD.id;
    end if;
end//

DELIMITER ;