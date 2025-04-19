package net.blueshell.api.util;

import java.security.SecureRandom;
import java.util.Random;

public class Util {

    private static final Random random = new SecureRandom();

    public static String getCallerMethod(int stackFramePosition) {
        var stackFrames = Thread.currentThread().getStackTrace();
        var caller = "Could not find caller?";
        if (stackFrames != null && stackFrames.length > stackFramePosition) {
            caller = stackFrames[stackFramePosition].toString();
        }
        return caller;
    }

    public static boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

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
