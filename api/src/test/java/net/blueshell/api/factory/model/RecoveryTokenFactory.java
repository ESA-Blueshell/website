package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.ResetType;
import net.blueshell.api.model.RecoveryToken;
import net.blueshell.api.model.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for RecoveryToken model test instances.
 */
@Component
@RequiredArgsConstructor
public class RecoveryTokenFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final UserFactory userFactory;

    public RecoveryToken createBasic() {
        RecoveryToken rt = new RecoveryToken();
        User u = userFactory.createBasic();
        rt.setUser(u);
        rt.setType(faker.options().option(ResetType.class));
        rt.setSelector(faker.crypto().sha256().substring(0, 32));
        rt.setVerifierHash(faker.crypto().sha256());
        rt.setExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
        rt.setConsumedAt(null);
        return rt;
    }

    public RecoveryToken createFull() {
        return createBasic();
    }

    public RecoveryToken createWithCustomizations(java.util.function.Consumer<RecoveryToken> customizer) {
        RecoveryToken rt = createFull();
        customizer.accept(rt);
        return rt;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
