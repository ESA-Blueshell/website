package net.blueshell.api.common.util;

import net.blueshell.api.model.User;

import java.security.SecureRandom;
import java.util.function.BiConsumer;

public class MappingUtil {
    private static final String CHAR_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*()-_=+<>?";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom random = new SecureRandom();


    public static <O, T> void applyIfFieldIsNotNull(O user, T obj, BiConsumer<O, T> applier) {
        if (obj != null) {
            applier.accept(user, obj);
        }
    }

    public static String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int index = random.nextInt(CHAR_SET.length());
            password.append(CHAR_SET.charAt(index));
        }
        return password.toString();
    }
}
