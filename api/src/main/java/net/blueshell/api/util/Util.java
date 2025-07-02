package net.blueshell.api.util;

import java.security.SecureRandom;
import java.util.Random;

public class Util {

    private static final Random random = new SecureRandom();

    public static String getRandomCapitalString(int length) {
        StringBuilder randKey = new StringBuilder();

        String options = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        for (int i = 1; i <= length; i++) {
            options = options.toUpperCase();
            randKey.append(options.charAt(Util.getRandom(options.length() - 1)));
        }

        return randKey.toString();
    }

    public static int getRandom(int inclBound) {
        if (inclBound <= 0) {
            return 0;
        }
        return random.nextInt(inclBound + 1);
    }
}
