package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.Redirect;
import net.blueshell.api.model.Telemetry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Redirect model test instances.
 */
@Component
@RequiredArgsConstructor
public class RedirectFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final TelemetryFactory telemetryFactory;

    public Redirect createBasic() {
        Redirect r = new Redirect();
        Telemetry t = telemetryFactory.createBasic();
        r.setTelemetry(t);
        return r;
    }

    public Redirect createFull() {
        return createBasic();
    }

    public Redirect createWithCustomizations(java.util.function.Consumer<Redirect> customizer) {
        Redirect r = createFull();
        customizer.accept(r);
        return r;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
