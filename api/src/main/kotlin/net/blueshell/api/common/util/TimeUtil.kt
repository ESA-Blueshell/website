package net.blueshell.api.common.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;

public class TimeUtil {

    public static boolean hasExpired(Timestamp timestamp) {
        if (timestamp == null) return false;

        return timestamp.before(Timestamp.from(Instant.now()));
    }

    public static Timestamp of(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;

        return Timestamp.valueOf(localDateTime);
    }

}
