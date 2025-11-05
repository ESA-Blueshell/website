package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.base.BaseModel;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for model factories.
 */
@RequiredArgsConstructor
public abstract class BaseFactory<T> {

    private static final AtomicLong idCounter = new AtomicLong(1000);
    protected final Faker faker;
    protected final PasswordEncoder passwordEncoder;
    protected final Random random;

    /** Create a basic instance with minimal required fields. */
    public abstract T createBasic();

    /** Create a fully populated instance. */
    public abstract T createFull();

    /** Create an instance with specific customizations. */
    public abstract T createWithCustomizations(java.util.function.Consumer<T> customizer);

    protected Long generateId() {
        return idCounter.incrementAndGet();
    }

    protected Instant futureInstant() {
        return LocalDateTime.now().plusDays(faker.number().numberBetween(1, 365))
                .atZone(ZoneId.systemDefault()).toInstant();
    }

    protected Instant pastInstant() {
        return LocalDateTime.now().minusDays(faker.number().numberBetween(1, 365))
                .atZone(ZoneId.systemDefault()).toInstant();
    }

    protected LocalDate futureDate() {
        return LocalDate.now().plusDays(faker.number().numberBetween(1, 365));
    }

    protected LocalDate pastDate() {
        return LocalDate.now().minusDays(faker.number().numberBetween(1, 365));
    }

    /** Hook to set base entity fields if needed (usually set by JPA). */
    protected <M extends BaseModel> void setBaseFields(M entity) {
        // Intentionally no-op for tests
    }
}
