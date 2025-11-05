package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for User model test instances.
 */
@Component
@RequiredArgsConstructor
public class UserFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final PasswordEncoder passwordEncoder;
    private final Random random;
    private final AddressFactory addressFactory;

    public User createBasic() {
        User user = new User();
        user.setId(generateId());
        user.setUsername(faker.name().username().toLowerCase().replaceAll("[^a-z0-9]", ""));
        user.setPassword(passwordEncoder.encode("password123"));
        user.setFirstName(faker.name().firstName());
        user.setLastName(faker.name().lastName());
        user.setEmail(faker.internet().emailAddress().toLowerCase());
        user.setEnabled(true);
        user.setNewsletter(faker.bool().bool());
        user.setRoles(EnumSet.of(Role.GUEST));
        return user;
    }

    public User createFull() {
        User user = createBasic();
        user.setPrefix(faker.name().prefix());
        user.setInitials(generateInitials(user.getFirstName(), user.getLastName()));
        user.setAddress(addressFactory.createBasic());
        user.setPhoneNumber(faker.phoneNumber().phoneNumber());
        user.setStudentNumber(String.valueOf(faker.number().numberBetween(1000000, 9999999)));
        user.setDateOfBirth(Date.valueOf(LocalDate.now().minusYears(faker.number().numberBetween(18, 30))));
        user.setDiscord(faker.name().username() + "#" + faker.number().numberBetween(1000, 9999));
        user.setSteamid(String.valueOf(faker.number().randomNumber(17, true)));
        user.setConsentPrivacy(true);
        user.setConsentGdpr(true);
        user.setGender(faker.options().option("Male", "Female", "Other"));
        user.setPhotoConsent(faker.bool().bool());
        user.setNationality(faker.nation().nationality());
        user.setEhbo(faker.bool().bool());
        user.setBhv(faker.bool().bool());
        user.setStudy(faker.educator().course());
        user.setStartStudyYear((long) faker.number().numberBetween(2018, 2023));

        if (faker.bool().bool()) {
            user.getRoles().add(Role.MEMBER);
        }
        if (faker.bool().bool()) {
            user.getRoles().add(faker.options().option(Role.COMMITTEE, Role.BOARD));
        }

        return user;
    }

    public User createWithCustomizations(java.util.function.Consumer<User> customizer) {
        User user = createFull();
        customizer.accept(user);
        return user;
    }

    public User createWithRole(Role role) {
        return createWithCustomizations(user -> {
            user.getRoles().clear();
            user.getRoles().add(role);
        });
    }

    public User createAdmin() {
        return createWithRole(Role.ADMIN);
    }

    public User createBoardMember() {
        return createWithRole(Role.BOARD);
    }

    public User createCommitteeMember() {
        return createWithRole(Role.COMMITTEE);
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }

    private String generateInitials(String firstName, String lastName) {
        return (firstName.charAt(0) + ". " + lastName.charAt(0)).toUpperCase();
    }
}
