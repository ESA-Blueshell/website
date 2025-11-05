package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.Address;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Address model test instances.
 */
@Component
@RequiredArgsConstructor
public class AddressFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;

    public Address createBasic() {
        Address address = new Address();
        address.setId(generateId());
        address.setCountry("Netherlands");
        address.setCity(faker.address().city());
        address.setStreet(faker.address().streetName());
        address.setHouseNumber(String.valueOf(faker.number().numberBetween(1, 999)));
        address.setZipCode(generateDutchZipCode());
        return address;
    }

    public Address createFull() {
        return createBasic();
    }

    public Address createWithCustomizations(java.util.function.Consumer<Address> customizer) {
        Address address = createFull();
        customizer.accept(address);
        return address;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }

    private String generateDutchZipCode() {
        return faker.number().numberBetween(1000, 9999) + " " + faker.letterify("??").toUpperCase();
    }
}
