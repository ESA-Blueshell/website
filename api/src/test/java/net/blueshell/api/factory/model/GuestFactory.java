package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.event.Guest;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Guest model test instances.
 */
@Component
@RequiredArgsConstructor
public class GuestFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;

    public Guest createBasic() {
        Guest guest = new Guest();
        guest.setId(generateId());
        guest.setName(faker.name().fullName());
        guest.setDiscord(faker.name().username() + "#" + faker.number().numberBetween(1000, 9999));
        guest.setEmail(faker.internet().emailAddress());
        guest.setPhoneNumber(faker.phoneNumber().phoneNumber());
        guest.setAccessToken(UUID.randomUUID().toString());
        return guest;
    }

    public Guest createFull() {
        return createBasic();
    }

    public Guest createWithCustomizations(java.util.function.Consumer<Guest> customizer) {
        Guest guest = createFull();
        customizer.accept(guest);
        return guest;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
