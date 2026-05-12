package net.blueshell.systemtests

/**
 * URLs and shard config read from `-D` system properties. Tests pick these
 * up at JVM startup. Defaults target a local dev compose stack so a Gradle
 * run with no -D flags still works against `docker compose up` from the
 * repo root.
 *
 * Mirrors personal-stack-2's pattern of carrying test config as system
 * properties prefixed `test.*` rather than env vars.
 */
object TestEnvironment {
    val apiUrl: String get() = sys("test.api.url", "http://localhost:8080")
    val frontendUrl: String get() = sys("test.frontend.url", "http://localhost:3000")
    val mailhogUrl: String get() = sys("test.mailhog.url", "http://localhost:8025")

    val shardIndex: Int? get() = System.getProperty("test.shard.index")?.toIntOrNull()
    val shardCount: Int? get() = System.getProperty("test.shard.count")?.toIntOrNull()?.takeIf { it > 1 }

    private fun sys(key: String, default: String): String =
        System.getProperty(key)?.takeIf { it.isNotBlank() } ?: default
}
