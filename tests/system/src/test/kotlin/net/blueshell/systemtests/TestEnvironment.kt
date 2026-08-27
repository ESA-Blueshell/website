package net.blueshell.systemtests

/**
 * URLs and shard config read from `-D` system properties. Tests pick
 * these up at JVM startup. Defaults target a local dev compose stack
 * so a Gradle run with no `-D` flags still works against
 * `docker compose up` from the repo root.
 */
object TestEnvironment {
    val apiUrl: String get() = sys("test.api.url", "http://localhost:8080")
    val frontendUrl: String get() = sys("test.frontend.url", "http://localhost:3000")

    /**
     * Stalwart admin HTTP endpoint — used by `StalwartMailClient` to
     * inspect delivered messages. Defaults to the dev port mapping
     * in `services/stalwart/docker-compose.yml` (`8085:8080`).
     */
    val stalwartUrl: String get() = sys("test.stalwart.url", "http://localhost:8085")

    /**
     * Credentials for Stalwart's admin API. Dev defaults are `admin`
     * for both, set by `STALWART_ADMIN_USER`/`STALWART_ADMIN_SECRET`.
     */
    val stalwartAdminUser: String get() = sys("test.stalwart.admin-user", "admin")
    val stalwartAdminSecret: String get() = sys("test.stalwart.admin-secret", "admin")

    /**
     * Name of the auth cookie returned by `POST /auth`. Configurable
     * because the api reads it from `security.auth-cookie.name`, and
     * deployed instances may override the default.
     */
    val authCookieName: String get() = sys("test.auth-cookie.name", "BSH_AUTH")

    val shardIndex: Int? get() = System.getProperty("test.shard.index")?.toIntOrNull()
    val shardCount: Int? get() = System.getProperty("test.shard.count")?.toIntOrNull()?.takeIf { it > 1 }

    private fun sys(key: String, default: String): String =
        System.getProperty(key)?.takeIf { it.isNotBlank() } ?: default
}
