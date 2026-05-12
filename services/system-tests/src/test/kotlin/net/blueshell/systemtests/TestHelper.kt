package net.blueshell.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import java.net.ConnectException
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * HTTP + JDBC helper for system tests. The api drives behaviour over
 * HTTP (`POST /users`, `POST /auth`); JDBC fills the gaps where the
 * public surface won't help — flipping a fresh user from disabled to
 * enabled (no admin endpoint exposes it) and assigning roles in the
 * `authorities` join table (only mutable via the privileged
 * `ToggleUserRole` endpoint, which requires an existing admin caller
 * — a chicken-and-egg the tests cut by talking to the DB directly).
 *
 * Activation tokens are intentionally not retrieved from the DB: the
 * api only persists a hashed verifier (see `recovery_tokens`), so the
 * plaintext token in the email cannot be reconstructed from SQL.
 * Tests that exercise the activation flow itself read the email via
 * `StalwartMailClient`; every other test bypasses by setting
 * `enabled = true`.
 */
object TestHelper {
    private const val API_RETRY_ATTEMPTS = 3
    private const val API_RETRY_DELAY_MS = 2_000L
    private const val ACTIVE_ROW_PREDICATE = "deleted_at = '9999-12-31 23:59:59'"

    val apiBaseUrl: String get() = TestEnvironment.apiUrl

    private val dbUrl: String
        get() = System.getProperty("test.db.url", "jdbc:mariadb://localhost:3306/blueshell")
    private val dbUser: String
        get() = System.getProperty("test.db.user", "blueshell")
    private val dbPassword: String
        get() = System.getProperty("test.db.password", "ci-blueshell")

    fun givenApi(): RequestSpecification = given().relaxedHTTPSValidation()

    /**
     * Build a request that carries a fresh CSRF round-trip. The api
     * uses Spring Security's BREACH-protected token: the
     * `Set-Cookie XSRF-TOKEN=` value is XOR-encoded per request, and
     * `GET /csrf` returns the raw token in its JSON body. State-
     * changing requests have to send the cookie value verbatim and the
     * body token as `X-XSRF-TOKEN`; the frontend follows the same
     * shape in `services/frontend/src/services/api/blueshell.runtime.ts`.
     */
    fun givenCsrfApi(): RequestSpecification {
        val csrfResponse = retryOnConnectionFailure {
            givenApi().baseUri(apiBaseUrl).`when`().get("/csrf")
        }
        require(csrfResponse.statusCode == 200) {
            "GET /csrf returned ${csrfResponse.statusCode}: ${csrfResponse.asString()}"
        }
        val cookieValue = csrfResponse.cookie("XSRF-TOKEN")
            ?: error("no XSRF-TOKEN cookie in /csrf response")
        val bodyToken = csrfResponse.jsonPath().getString("token")
            ?: error("no token field in /csrf response body")
        return givenApi()
            .cookie("XSRF-TOKEN", cookieValue)
            .header("X-XSRF-TOKEN", bodyToken)
    }

    /**
     * Standard password for created test users. Passes the api's
     * complexity rule (lower + upper + digit + one of `@$!%*?&`).
     */
    const val DEFAULT_PASSWORD: String = "Password123!"

    private fun <T> retryOnConnectionFailure(action: () -> T): T {
        var lastException: Exception? = null
        repeat(API_RETRY_ATTEMPTS) { attempt ->
            try {
                return action()
            } catch (e: Exception) {
                if (e is ConnectException || e.cause is ConnectException) {
                    lastException = e
                    if (attempt < API_RETRY_ATTEMPTS - 1) {
                        Thread.sleep(API_RETRY_DELAY_MS * (attempt + 1))
                    }
                } else {
                    throw e
                }
            }
        }
        throw lastException!!
    }

    /**
     * Register a new user via `POST /users`. The account lands
     * disabled — the api flips `enabled = false` on every fresh
     * registration. Callers that want a login-ready account should
     * chain `setEnabled(user.username, true)` or use
     * `registerAndActivate`.
     */
    fun register(
        username: String = "sys_${UUID.randomUUID().toString().take(8)}",
        password: String = DEFAULT_PASSWORD,
        email: String = "$username@systemtest.example.com",
    ): RegisteredUser {
        val response = retryOnConnectionFailure {
            givenCsrfApi()
                .baseUri(apiBaseUrl)
                .contentType(ContentType.JSON)
                .body(
                    """
                    {
                      "username": "$username",
                      "email": "$email",
                      "initials": "TU",
                      "firstName": "Test",
                      "lastName": "User",
                      "discord": "$username#0001",
                      "phoneNumber": "06${System.currentTimeMillis().toString().takeLast(8)}",
                      "newsletter": false,
                      "consentPrivacy": true,
                      "photoConsent": false,
                      "password": "$password"
                    }
                    """.trimIndent(),
                ).`when`()
                .post("/users")
        }
        require(response.statusCode == 201) {
            "POST /users returned ${response.statusCode}: ${response.asString()}"
        }

        return RegisteredUser(username, email, password)
    }

    /**
     * Convenience: register + flip `enabled = true`. Standard setup
     * for any test that wants a user it can immediately log in as.
     */
    fun registerAndActivate(
        username: String = "sys_${UUID.randomUUID().toString().take(8)}",
        password: String = DEFAULT_PASSWORD,
        email: String = "$username@systemtest.example.com",
    ): RegisteredUser {
        val user = register(username, password, email)
        setEnabled(user.username, true)
        return user
    }

    /**
     * Register, activate, and replace the user's roles with exactly
     * the requested one (every other row in `authorities` for the
     * user is removed first).
     */
    fun registerActivateAndPromote(
        role: String,
        username: String = "${role.lowercase()}_${UUID.randomUUID().toString().take(8)}",
        password: String = DEFAULT_PASSWORD,
    ): RegisteredUser {
        val user = registerAndActivate(username = username, password = password)
        replaceRoles(user.username, setOf(role))
        return user
    }

    /**
     * Toggle `users.enabled`. Used to mint a deliberately-disabled
     * account for tests that exercise the login-blocked path, and
     * internally to activate freshly-registered users.
     */
    fun setEnabled(username: String, enabled: Boolean) {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "UPDATE users SET enabled = ? WHERE username = ? AND $ACTIVE_ROW_PREDICATE",
            ).use { stmt ->
                stmt.setBoolean(1, enabled)
                stmt.setString(2, username)
                require(stmt.executeUpdate() == 1) {
                    "Failed to set enabled=$enabled on username=$username"
                }
            }
        }
    }

    /**
     * Replace every row in `authorities` for the given user. New users
     * start with `GUEST` only; tests that want exactly `MEMBER` (or
     * any other single role) should call this rather than appending.
     */
    fun replaceRoles(username: String, roles: Set<String>) {
        require(roles.isNotEmpty()) { "Refusing to leave $username with no roles" }
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.autoCommit = false
            try {
                val userId = userIdOrThrow(conn, username)
                conn.prepareStatement("DELETE FROM authorities WHERE user_id = ?").use { stmt ->
                    stmt.setLong(1, userId)
                    stmt.executeUpdate()
                }
                conn.prepareStatement(
                    "INSERT INTO authorities (user_id, authority) VALUES (?, ?)",
                ).use { stmt ->
                    for (role in roles) {
                        stmt.setLong(1, userId)
                        stmt.setString(2, role)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /**
     * Append a single role without disturbing existing ones. Use when
     * the test wants role inheritance to compose (e.g. a `MEMBER` who
     * is also `BOARD`).
     */
    fun grantRole(username: String, role: String) {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            val userId = userIdOrThrow(conn, username)
            conn.prepareStatement(
                "INSERT IGNORE INTO authorities (user_id, authority) VALUES (?, ?)",
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, role)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * Read a user back from the DB. Returns null when the user doesn't
     * exist (or is soft-deleted). Used by tests that previously polled
     * `userRepository.findByUsername(...)` to verify async writes.
     */
    fun findUser(username: String): RegisteredUserRow? =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "SELECT id, username, email, enabled FROM users " +
                    "WHERE username = ? AND $ACTIVE_ROW_PREDICATE",
            ).use { stmt ->
                stmt.setString(1, username)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    RegisteredUserRow(
                        id = rs.getLong("id"),
                        username = rs.getString("username"),
                        email = rs.getString("email"),
                        enabled = rs.getBoolean("enabled"),
                    )
                } else {
                    null
                }
            }
        }

    /**
     * Hits `POST /auth` and returns the auth cookie (default name
     * `BSH_AUTH`, overridable via `-Dtest.auth-cookie.name=...`) so
     * callers can forward it into a Playwright `BrowserContext` or
     * onto a follow-up `HttpClient` request.
     */
    fun login(user: RegisteredUser): LoginCookies {
        val response = retryOnConnectionFailure {
            givenCsrfApi()
                .baseUri(apiBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"username":"${user.username}","password":"${user.password}"}""")
                .`when`()
                .post("/auth")
        }
        require(response.statusCode in 200..204) {
            "Login for ${user.username} failed: ${response.statusCode} ${response.asString()}"
        }
        return LoginCookies(
            auth = response.cookie(TestEnvironment.authCookieName)
                ?: error("no ${TestEnvironment.authCookieName} cookie in /auth response"),
            csrf = response.cookie("XSRF-TOKEN"),
        )
    }

    private fun userIdOrThrow(conn: Connection, username: String): Long {
        conn.prepareStatement(
            "SELECT id FROM users WHERE username = ? AND $ACTIVE_ROW_PREDICATE",
        ).use { stmt ->
            stmt.setString(1, username)
            val rs = stmt.executeQuery()
            require(rs.next()) { "No active user with username=$username" }
            return rs.getLong("id")
        }
    }

    data class RegisteredUser(
        val username: String,
        val email: String,
        val password: String,
    )

    data class RegisteredUserRow(
        val id: Long,
        val username: String,
        val email: String,
        val enabled: Boolean,
    )

    data class LoginCookies(
        val auth: String,
        val csrf: String?,
    )
}
