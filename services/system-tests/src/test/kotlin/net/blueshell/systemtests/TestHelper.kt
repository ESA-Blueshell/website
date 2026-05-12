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
        discord: String = "$username#0001",
        phoneNumber: String = "06${System.currentTimeMillis().toString().takeLast(8)}",
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
                      "discord": "$discord",
                      "phoneNumber": "$phoneNumber",
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

        return RegisteredUser(
            username = username,
            email = email,
            password = password,
            discord = discord,
            phoneNumber = phoneNumber,
        )
    }

    /**
     * Convenience: register + flip `enabled = true`. Standard setup
     * for any test that wants a user it can immediately log in as.
     */
    fun registerAndActivate(
        username: String = "sys_${UUID.randomUUID().toString().take(8)}",
        password: String = DEFAULT_PASSWORD,
        email: String = "$username@systemtest.example.com",
        discord: String = "$username#0001",
        phoneNumber: String = "06${System.currentTimeMillis().toString().takeLast(8)}",
    ): RegisteredUser {
        val user = register(username, password, email, discord, phoneNumber)
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
        email: String = "$username@systemtest.example.com",
        discord: String = "$username#0001",
        phoneNumber: String = "06${System.currentTimeMillis().toString().takeLast(8)}",
    ): RegisteredUser {
        val user = registerAndActivate(
            username = username,
            password = password,
            email = email,
            discord = discord,
            phoneNumber = phoneNumber,
        )
        replaceRoles(user.username, setOf(role))
        return user
    }

    /**
     * Run the api's user-erasure flow against `username`. A plain
     * JDBC soft-delete is not enough: `UserErasureService.deleteUser`
     * also anonymises identifying columns, sets `enabled = false`,
     * drops the member-profile / address links, and writes a
     * `DeletedUser` snapshot the address manager / recovery manager
     * panels read from. The simplest reproduction is to register a
     * fresh admin, log them in, and post `DELETE /users/{id}` against
     * the target — the api then runs the same service it would for a
     * real admin click.
     */
    fun eraseUser(username: String) {
        val target = findUser(username) ?: error("No active user with username=$username")
        val admin = registerActivateAndPromote(
            role = "ADMIN",
            username = "eraser_${UUID.randomUUID().toString().take(8)}",
        )
        val cookies = login(admin)
        val response = retryOnConnectionFailure {
            givenCsrfApi()
                .baseUri(apiBaseUrl)
                .cookie(TestEnvironment.authCookieName, cookies.auth)
                .`when`()
                .delete("/users/${target.id}")
        }
        require(response.statusCode == 204) {
            "DELETE /users/${target.id} returned ${response.statusCode}: ${response.asString()}"
        }
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
     * Read the address row currently linked to `username`, if any. Used
     * by tests that previously inspected `User.address?.field` after a
     * Playwright-driven form submit.
     */
    fun findAddress(username: String): AddressRow? =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "SELECT a.id, a.country, a.city, a.street, a.house_number, a.zip_code " +
                    "FROM addresses a " +
                    "JOIN users u ON u.address_id = a.id " +
                    "WHERE u.username = ? AND u.$ACTIVE_ROW_PREDICATE " +
                    "AND a.$ACTIVE_ROW_PREDICATE",
            ).use { stmt ->
                stmt.setString(1, username)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    AddressRow(
                        id = rs.getLong("id"),
                        country = rs.getString("country"),
                        city = rs.getString("city"),
                        street = rs.getString("street"),
                        houseNumber = rs.getString("house_number"),
                        zipCode = rs.getString("zip_code"),
                    )
                } else {
                    null
                }
            }
        }

    /**
     * Insert an `addresses` row for `username` and point the user's
     * `address_id` column at it. Pre-seeds the address that a follow-up
     * test edits through the UI. Returns the new address id.
     */
    fun attachAddress(
        user: RegisteredUser,
        country: String = "NL",
        city: String,
        street: String,
        houseNumber: String,
        zipCode: String,
    ): Long {
        val cookies = login(user)
        val userId = findUser(user.username)!!.id
        val response = retryOnConnectionFailure {
            givenCsrfApi()
                .baseUri(apiBaseUrl)
                .cookie(TestEnvironment.authCookieName, cookies.auth)
                .contentType(ContentType.JSON)
                .body(
                    """
                    {
                      "userId": $userId,
                      "country": "$country",
                      "city": "$city",
                      "street": "$street",
                      "houseNumber": "$houseNumber",
                      "zipCode": "$zipCode"
                    }
                    """.trimIndent(),
                ).`when`()
                .post("/addresses")
        }
        require(response.statusCode == 201) {
            "POST /addresses returned ${response.statusCode}: ${response.asString()}"
        }
        return response.jsonPath().getLong("id")
    }

    /**
     * Read a user back from the DB. Returns null when the user doesn't
     * exist (or is soft-deleted). Used by tests that previously polled
     * `userRepository.findByUsername(...)` to verify async writes.
     */
    fun findUser(username: String): RegisteredUserRow? =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "SELECT id, username, email, enabled, discord, phone_number " +
                    "FROM users WHERE username = ? AND $ACTIVE_ROW_PREDICATE",
            ).use { stmt ->
                stmt.setString(1, username)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    RegisteredUserRow(
                        id = rs.getLong("id"),
                        username = rs.getString("username"),
                        email = rs.getString("email"),
                        enabled = rs.getBoolean("enabled"),
                        discord = rs.getString("discord"),
                        phoneNumber = rs.getString("phone_number"),
                    )
                } else {
                    null
                }
            }
        }

    /**
     * Attach a `memberships` row directly via JDBC. The api's
     * `POST /memberships` requires the calling user to have both a
     * profile and an address, and `POST /users/{id}/memberships`
     * requires a board-level caller — neither shape fits a test that
     * just wants a member-status user as a precondition. A plain
     * insert sidesteps both. Audit columns (`created_at`,
     * `updated_at`, `version`, `deleted_at`) have schema defaults;
     * `created_by_id` / `updated_by_id` are nullable. Returns the
     * new membership id.
     */
    fun attachMembership(
        username: String,
        memberType: String = "REGULAR",
        startDate: String = java.time.LocalDate.now().minusDays(30).toString(),
        incasso: Boolean = true,
    ): Long {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            val userId = userIdOrThrow(conn, username)
            return conn.prepareStatement(
                "INSERT INTO memberships (user_id, start_date, type, incasso) VALUES (?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, startDate)
                stmt.setString(3, memberType)
                stmt.setBoolean(4, incasso)
                stmt.executeUpdate()
                val keys = stmt.generatedKeys
                require(keys.next()) { "INSERT memberships produced no id" }
                keys.getLong(1)
            }
        }
    }

    /**
     * Truncate every row from `job_executions`. The api dispatches a
     * couple of background jobs as part of `POST /users` (contact
     * sync, activation email) with `app.jobs.auto-dispatch=true`, so
     * tests that assert exact stats counts have to wipe those carrier
     * rows after their setup completes.
     */
    fun clearJobExecutions() {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement("DELETE FROM job_executions").use { it.executeUpdate() }
        }
    }

    /**
     * Insert a `job_executions` row in the given status. The job
     * manager's stats endpoint reads counts and timings off this
     * table directly, so tests that want to exercise the stats panel
     * just need a few well-shaped rows. `queuedAt` / `startedAt` /
     * `finishedAt` follow the lifecycle implied by `status`:
     * a `SUCCESS` / `FAILED` row has all three set; a `RUNNING` /
     * `RETRYING` / `DEAD` row has `queuedAt` + `startedAt`; a
     * `QUEUED` row has only `queuedAt`.
     */
    fun createJobExecution(
        jobType: String,
        status: String = "SUCCESS",
        queuedAt: java.time.Instant = java.time.Instant.now().minusSeconds(600),
        startedAt: java.time.Instant? = java.time.Instant.now().minusSeconds(300),
        finishedAt: java.time.Instant? = java.time.Instant.now().minusSeconds(120),
        attempts: Int = 1,
    ): Long {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            return conn.prepareStatement(
                "INSERT INTO job_executions (job_type, status, attempts, queued_at, started_at, finished_at, " +
                    "initiated_by_type, initiated_by_role) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'SYSTEM', 'ADMIN')",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { stmt ->
                stmt.setString(1, jobType)
                stmt.setString(2, status)
                stmt.setInt(3, attempts)
                stmt.setTimestamp(4, java.sql.Timestamp.from(queuedAt))
                stmt.setTimestamp(5, startedAt?.let { java.sql.Timestamp.from(it) })
                stmt.setTimestamp(6, finishedAt?.let { java.sql.Timestamp.from(it) })
                stmt.executeUpdate()
                val keys = stmt.generatedKeys
                require(keys.next()) { "INSERT job_executions produced no id" }
                keys.getLong(1)
            }
        }
    }

    /**
     * Attach a `member_profiles` row to a user via `POST /memberProfiles`.
     * Logs the user in to satisfy the controller's `hasPermission(userId,
     * 'User', 'write')` guard. Defaults cover the columns the api marks
     * `@field:NotNull` / `@field:NotBlank`; callers can override any of
     * them.
     */
    fun attachMemberProfile(
        user: RegisteredUser,
        dateOfBirth: String = "1999-05-05",
        studentNumber: String = "s${System.currentTimeMillis()}",
        gender: String = "X",
        nationality: String = "NL",
        bhv: Boolean = false,
        ehbo: Boolean = false,
    ): Long {
        val cookies = login(user)
        val userId = findUser(user.username)!!.id
        val response = retryOnConnectionFailure {
            givenCsrfApi()
                .baseUri(apiBaseUrl)
                .cookie(TestEnvironment.authCookieName, cookies.auth)
                .contentType(ContentType.JSON)
                .body(
                    """
                    {
                      "userId": $userId,
                      "dateOfBirth": "$dateOfBirth",
                      "studentNumber": "$studentNumber",
                      "gender": "$gender",
                      "nationality": "$nationality",
                      "bhv": $bhv,
                      "ehbo": $ehbo
                    }
                    """.trimIndent(),
                ).`when`()
                .post("/memberProfiles")
        }
        require(response.statusCode == 201) {
            "POST /memberProfiles returned ${response.statusCode}: ${response.asString()}"
        }
        return response.jsonPath().getLong("id")
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
        val discord: String,
        val phoneNumber: String,
    )

    data class RegisteredUserRow(
        val id: Long,
        val username: String,
        val email: String,
        val enabled: Boolean,
        val discord: String?,
        val phoneNumber: String?,
    )

    data class AddressRow(
        val id: Long,
        val country: String?,
        val city: String?,
        val street: String?,
        val houseNumber: String?,
        val zipCode: String?,
    )

    data class LoginCookies(
        val auth: String,
        val csrf: String?,
    )
}
