package net.blueshell.api.factory.dto;

import com.github.javafaker.Faker;
import jakarta.persistence.MappedSuperclass;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Base support for DTO factories to produce reproducible test fixtures.
 */
@MappedSuperclass
public abstract class BaseDtoFactory<T> {

    private static final AtomicLong SEQ = new AtomicLong(1000);

    protected final Faker faker;
    protected final PasswordEncoder passwordEncoder;
    protected final Random random;

    protected BaseDtoFactory(Faker faker, PasswordEncoder passwordEncoder, Random random) {
        this.faker = faker;
        this.passwordEncoder = passwordEncoder;
        this.random = random;
    }

    protected BaseDtoFactory() {
        this(new Faker(), NoOpPasswordEncoder.getInstance(), new Random());
    }

    /** The DTO class this factory produces; used by the registry. */
    public abstract Class<T> targetType();

    /** Minimal valid instance. */
    public abstract T createBasic();

    /** Fully populated instance (defaults to basic). */
    public T createFull() {
        return createBasic();
    }

    /** Instance with inline tweaks. */
    public T createWithCustomizations(Consumer<T> customizer) {
        T t = createFull();
        if (customizer != null) customizer.accept(t);
        return t;
    }

    protected long nextId() {
        return SEQ.incrementAndGet();
    }

    protected String unique(String prefix) {
        return prefix + "-" + nextId();
    }

    protected String email(String local) {
        return (local + "+" + nextId() + "@test.com").toLowerCase();
    }

    protected Instant now() {
        return Instant.now().atZone(ZoneId.of("Europe/Amsterdam")).toInstant();
    }

    protected LocalDate today() {
        return LocalDate.now(ZoneId.of("Europe/Amsterdam"));
    }
}
