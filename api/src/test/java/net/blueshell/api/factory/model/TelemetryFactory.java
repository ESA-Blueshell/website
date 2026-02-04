package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.PlatformType;
import net.blueshell.api.model.Telemetry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Telemetry model test instances.
 */
@Component
@RequiredArgsConstructor
public class TelemetryFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;

    public Telemetry createBasic() {
        Telemetry t = new Telemetry();
        ModelTestUtils.setId(t, generateId());
        t.setPlatform(faker.options().option(PlatformType.class));
        t.setUrl(faker.internet().url());
        return t;
    }

    public Telemetry createFull() {
        return createBasic();
    }

    public Telemetry createWithCustomizations(java.util.function.Consumer<Telemetry> customizer) {
        Telemetry t = createFull();
        customizer.accept(t);
        return t;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
