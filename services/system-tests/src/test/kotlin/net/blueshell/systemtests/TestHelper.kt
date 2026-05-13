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
    private const val SELECTOR_BYTES = 16
    private const val VERIFIER_BYTES = 32
    private const val EVENT_SELECT =
        "SELECT id, title, description, location, approved, sign_up, members_only, " +
            "committee_id, sign_up_limit FROM events "

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
        firstName: String = "Test",
        lastName: String = "User",
        initials: String = "TU",
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
                      "initials": "$initials",
                      "firstName": "$firstName",
                      "lastName": "$lastName",
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
            firstName = firstName,
            lastName = lastName,
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
        firstName: String = "Test",
        lastName: String = "User",
    ): RegisteredUser {
        val user = register(username, password, email, discord, phoneNumber, firstName, lastName)
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
        firstName: String = "Test",
        lastName: String = "User",
    ): RegisteredUser {
        val user = registerAndActivate(
            username = username,
            password = password,
            email = email,
            discord = discord,
            phoneNumber = phoneNumber,
            firstName = firstName,
            lastName = lastName,
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
     * Read a user back from the DB by id. Returns null when the user
     * was hard-deleted; soft-deleted users are *not* filtered (the
     * recovery flow restores them so the test needs to see the row
     * regardless of `deleted_at`).
     */
    fun findUserById(userId: Long): RegisteredUserRow? =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "SELECT id, username, email, enabled, discord, phone_number FROM users WHERE id = ?",
            ).use { stmt ->
                stmt.setLong(1, userId)
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
     * Returns true when a row exists in `deleted_users` for the given
     * user id — the snapshot table `UserErasureService.deleteUser(...)`
     * writes to. Empty after the user is restored.
     */
    fun hasDeletedUserSnapshot(userId: Long): Boolean =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "SELECT 1 FROM deleted_users WHERE id = ? LIMIT 1",
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.executeQuery().next()
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
        startDate: java.time.LocalDate = java.time.LocalDate.now().minusDays(30),
        endDate: java.time.LocalDate? = null,
        incasso: Boolean = true,
    ): Long {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            val userId = userIdOrThrow(conn, username)
            return conn.prepareStatement(
                "INSERT INTO memberships (user_id, start_date, end_date, type, incasso) " +
                    "VALUES (?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setDate(2, java.sql.Date.valueOf(startDate))
                if (endDate != null) {
                    stmt.setDate(3, java.sql.Date.valueOf(endDate))
                } else {
                    stmt.setNull(3, java.sql.Types.DATE)
                }
                stmt.setString(4, memberType)
                stmt.setBoolean(5, incasso)
                stmt.executeUpdate()
                val keys = stmt.generatedKeys
                require(keys.next()) { "INSERT memberships produced no id" }
                keys.getLong(1)
            }
        }
    }

    /**
     * Look up a single membership row by id. Returns null when no
     * active row exists.
     */
    fun findMembership(membershipId: Long): MembershipRow? =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "SELECT id, user_id, start_date, end_date, type, incasso " +
                    "FROM memberships WHERE id = ? AND $ACTIVE_ROW_PREDICATE",
            ).use { stmt ->
                stmt.setLong(1, membershipId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    MembershipRow(
                        id = rs.getLong("id"),
                        userId = rs.getLong("user_id"),
                        startDate = rs.getDate("start_date").toLocalDate(),
                        endDate = rs.getDate("end_date")?.toLocalDate(),
                        type = rs.getString("type"),
                        incasso = rs.getBoolean("incasso"),
                    )
                } else {
                    null
                }
            }
        }

    /**
     * Returns true when the user has an active (end_date IS NULL)
     * membership row. Mirrors `MemberRepository.existsByUser_IdAndEndDateIsNull`.
     */
    fun hasActiveMembership(username: String): Boolean =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            val userId = userIdOrThrow(conn, username)
            conn.prepareStatement(
                "SELECT 1 FROM memberships " +
                    "WHERE user_id = ? AND end_date IS NULL AND $ACTIVE_ROW_PREDICATE LIMIT 1",
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.executeQuery().next()
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
        errorType: String? = null,
        errorReason: String? = null,
    ): Long {
        val errorMessage = if (errorType != null && errorReason != null) "$errorType: $errorReason" else null
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            return conn.prepareStatement(
                "INSERT INTO job_executions " +
                    "(job_type, status, attempts, queued_at, started_at, finished_at, " +
                    "error_type, error_reason, error_message, initiated_by_type, initiated_by_role) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SYSTEM', 'ADMIN')",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { stmt ->
                stmt.setString(1, jobType)
                stmt.setString(2, status)
                stmt.setInt(3, attempts)
                stmt.setTimestamp(4, java.sql.Timestamp.from(queuedAt))
                stmt.setTimestamp(5, startedAt?.let { java.sql.Timestamp.from(it) })
                stmt.setTimestamp(6, finishedAt?.let { java.sql.Timestamp.from(it) })
                stmt.setString(7, errorType)
                stmt.setString(8, errorReason)
                stmt.setString(9, errorMessage)
                stmt.executeUpdate()
                val keys = stmt.generatedKeys
                require(keys.next()) { "INSERT job_executions produced no id" }
                keys.getLong(1)
            }
        }
    }

    /**
     * Read a `job_executions` row back by id. Returns null when the
     * row does not exist. Used by tests that previously polled
     * `jobExecutionRepository.findById(id)` to verify the retry
     * pipeline mutated `status` / `queued_at`.
     */
    fun findJobExecution(id: Long): JobExecutionRow? =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "SELECT id, job_type, status, attempts, queued_at, started_at, finished_at " +
                    "FROM job_executions WHERE id = ?",
            ).use { stmt ->
                stmt.setLong(1, id)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    JobExecutionRow(
                        id = rs.getLong("id"),
                        jobType = rs.getString("job_type"),
                        status = rs.getString("status"),
                        attempts = rs.getInt("attempts"),
                        queuedAt = rs.getTimestamp("queued_at")?.toInstant(),
                        startedAt = rs.getTimestamp("started_at")?.toInstant(),
                        finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
                    )
                } else {
                    null
                }
            }
        }

    /**
     * Query the test-only `/test-support/emails` endpoint for emails
     * the in-process `MockListmonkEmailClient` captured. Empty list
     * when nothing matches. Used by tests that previously asserted
     * `emailTransportClient.sentEmails.any { … }`.
     */
    fun findEmails(recipient: String? = null, subject: String? = null): List<SentEmail> {
        val response = retryOnConnectionFailure {
            var spec = givenApi().baseUri(apiBaseUrl)
            if (recipient != null) spec = spec.queryParam("recipient", recipient)
            if (subject != null) spec = spec.queryParam("subject", subject)
            spec.`when`().get("/test-support/emails")
        }
        require(response.statusCode == 200) {
            "GET /test-support/emails returned ${response.statusCode}: ${response.asString()}"
        }
        return response.jsonPath().getList("", Map::class.java).map { row ->
            @Suppress("UNCHECKED_CAST")
            val asMap = row as Map<String, Any?>
            SentEmail(
                toEmail = asMap["toEmail"] as String,
                toName = asMap["toName"] as String,
                subject = asMap["subject"] as String,
                htmlContent = asMap["htmlContent"] as String,
            )
        }
    }

    /**
     * Polls `findEmails(...)` until at least one email arrives that
     * matches the recipient + subject filter. Replaces the
     * `assertEmailSent(...)` helper the in-process base used to expose.
     */
    fun assertEmailSent(
        recipient: String,
        subject: String,
        timeoutMs: Long = 10_000,
    ): SentEmail {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val matches = findEmails(recipient = recipient, subject = subject)
            if (matches.isNotEmpty()) return matches.first()
            Thread.sleep(200)
        }
        throw AssertionError(
            "Expected an email subject=\"$subject\" recipient=$recipient within ${timeoutMs}ms",
        )
    }

    /**
     * Mint a recovery token directly via JDBC. The api's
     * `RecoveryTokenFactory.issue(...)` returns "selector.verifier"
     * — selector is a 16-byte URL-safe random, verifier is 32 bytes,
     * and only the BCrypt hash of the verifier lands in the table.
     * This helper reproduces all three steps so tests that exercise
     * the activation / password-reset flows can plant a known
     * plaintext token without going through the in-process factory.
     *
     * `type` accepts the `ResetType` enum names:
     * `USER_ACTIVATION`, `MEMBER_ACTIVATION`, `PASSWORD_RESET`.
     */
    fun mintRecoveryToken(
        username: String,
        type: String,
        ttl: java.time.Duration = java.time.Duration.ofDays(7),
    ): String {
        val selector = randomUrlSafe(SELECTOR_BYTES)
        val verifier = randomUrlSafe(VERIFIER_BYTES)
        val verifierHash = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
            .encode(verifier)
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            val userId = userIdOrThrow(conn, username)
            // Remove any prior unconsumed token of the same type for
            // this user — `RecoveryTokenFactory.issue(...)` deletes
            // those before inserting the new row.
            conn.prepareStatement(
                "DELETE FROM recovery_tokens " +
                    "WHERE user_id = ? AND type = ? AND consumed_at IS NULL " +
                    "AND $ACTIVE_ROW_PREDICATE",
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, type)
                stmt.executeUpdate()
            }
            conn.prepareStatement(
                "INSERT INTO recovery_tokens " +
                    "(user_id, type, selector, verifier_hash, expires_at, created_at, updated_at, version) " +
                    "VALUES (?, ?, ?, ?, ?, NOW(), NOW(), 0)",
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setString(2, type)
                stmt.setString(3, selector)
                stmt.setString(4, verifierHash)
                stmt.setTimestamp(5, java.sql.Timestamp.from(java.time.Instant.now().plus(ttl)))
                stmt.executeUpdate()
            }
        }
        return "$selector.$verifier"
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        java.security.SecureRandom().nextBytes(bytes)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Insert a `contribution_periods` row. Returns the new id.
     */
    fun createContributionPeriod(
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate,
        halfYearFee: Double = 0.0,
        fullYearFee: Double = 0.0,
        alumniFee: Double = 0.0,
    ): Long {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            return conn.prepareStatement(
                "INSERT INTO contribution_periods " +
                    "(start_date, end_date, half_year_fee, full_year_fee, alumni_fee) " +
                    "VALUES (?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { stmt ->
                stmt.setDate(1, java.sql.Date.valueOf(startDate))
                stmt.setDate(2, java.sql.Date.valueOf(endDate))
                stmt.setDouble(3, halfYearFee)
                stmt.setDouble(4, fullYearFee)
                stmt.setDouble(5, alumniFee)
                stmt.executeUpdate()
                val keys = stmt.generatedKeys
                require(keys.next()) { "INSERT contribution_periods produced no id" }
                keys.getLong(1)
            }
        }
    }

    /**
     * Mark a user as paid for a contribution period. Inserts a row in
     * `contributions` keyed on (user_id, contribution_period_id).
     */
    fun createContribution(periodId: Long, username: String) {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            val userId = userIdOrThrow(conn, username)
            conn.prepareStatement(
                "INSERT INTO contributions (user_id, contribution_period_id) VALUES (?, ?)",
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setLong(2, periodId)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * Return all active `contributions` rows for the given period as
     * `(userId, periodId)` pairs.
     */
    fun findContributions(periodId: Long): List<Long> =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "SELECT user_id FROM contributions " +
                    "WHERE contribution_period_id = ? AND $ACTIVE_ROW_PREDICATE",
            ).use { stmt ->
                stmt.setLong(1, periodId)
                val rs = stmt.executeQuery()
                val userIds = mutableListOf<Long>()
                while (rs.next()) {
                    userIds += rs.getLong("user_id")
                }
                userIds
            }
        }

    /**
     * Insert a `committees` row. Returns the new committee id.
     */
    fun createCommittee(
        name: String = "Committee ${UUID.randomUUID().toString().take(8)}",
        description: String = "Test committee",
    ): Long {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            return conn.prepareStatement(
                "INSERT INTO committees (name, description) VALUES (?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { stmt ->
                stmt.setString(1, name)
                stmt.setString(2, description)
                stmt.executeUpdate()
                val keys = stmt.generatedKeys
                require(keys.next()) { "INSERT committees produced no id" }
                keys.getLong(1)
            }
        }
    }

    /**
     * Insert a `committee_members` row linking the given user to a
     * committee. `role` is optional (matches the entity's nullable
     * column).
     */
    fun addCommitteeMember(committeeId: Long, username: String, role: String? = "Member") {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            val userId = userIdOrThrow(conn, username)
            conn.prepareStatement(
                "INSERT INTO committee_members (committee_id, user_id, role) VALUES (?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, committeeId)
                stmt.setLong(2, userId)
                stmt.setString(3, role)
                stmt.executeUpdate()
            }
        }
    }

    /**
     * Look up a `committees` row by id. Returns null when the
     * committee was soft-deleted or never existed.
     */
    fun findCommittee(committeeId: Long): CommitteeRow? =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "SELECT id, name, description FROM committees WHERE id = ? AND $ACTIVE_ROW_PREDICATE",
            ).use { stmt ->
                stmt.setLong(1, committeeId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    CommitteeRow(
                        id = rs.getLong("id"),
                        name = rs.getString("name"),
                        description = rs.getString("description"),
                    )
                } else {
                    null
                }
            }
        }

    /**
     * Return all active `committee_members` rows for the given
     * committee. Soft-deleted rows are filtered out.
     */
    fun findCommitteeMembers(committeeId: Long): List<CommitteeMemberRow> =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                "SELECT user_id, role FROM committee_members " +
                    "WHERE committee_id = ? AND $ACTIVE_ROW_PREDICATE",
            ).use { stmt ->
                stmt.setLong(1, committeeId)
                val rs = stmt.executeQuery()
                val rows = mutableListOf<CommitteeMemberRow>()
                while (rs.next()) {
                    rows += CommitteeMemberRow(
                        userId = rs.getLong("user_id"),
                        role = rs.getString("role"),
                    )
                }
                rows
            }
        }

    /**
     * Read the role authorities (`authorities.authority`) for the
     * given user. Returns an empty set when the user has no rows.
     */
    fun findRoles(username: String): Set<String> =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            val userId = userIdOrThrow(conn, username)
            conn.prepareStatement(
                "SELECT authority FROM authorities WHERE user_id = ?",
            ).use { stmt ->
                stmt.setLong(1, userId)
                val rs = stmt.executeQuery()
                val roles = mutableSetOf<String>()
                while (rs.next()) {
                    roles += rs.getString("authority")
                }
                roles
            }
        }

    /**
     * Insert an `events` row. Returns the new event id.
     */
    fun createEvent(
        committeeId: Long?,
        title: String,
        startTime: java.time.Instant = java.time.Instant.now().plusSeconds(7 * 24 * 3600),
        endTime: java.time.Instant = startTime.plusSeconds(3600),
        description: String? = "Event description",
        location: String? = "Campus",
        approved: Boolean = false,
        signUp: Boolean = false,
        membersOnly: Boolean = false,
        signUpLimit: Int? = null,
    ): Long {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            return conn.prepareStatement(
                "INSERT INTO events (committee_id, title, description, location, start_time, end_time, " +
                    "approved, members_only, sign_up, sign_up_limit) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { stmt ->
                if (committeeId != null) stmt.setLong(1, committeeId) else stmt.setNull(1, java.sql.Types.BIGINT)
                stmt.setString(2, title)
                stmt.setString(3, description)
                stmt.setString(4, location)
                stmt.setTimestamp(5, java.sql.Timestamp.from(startTime))
                stmt.setTimestamp(6, java.sql.Timestamp.from(endTime))
                stmt.setBoolean(7, approved)
                stmt.setBoolean(8, membersOnly)
                stmt.setBoolean(9, signUp)
                if (signUpLimit != null) stmt.setInt(10, signUpLimit) else stmt.setNull(10, java.sql.Types.INTEGER)
                stmt.executeUpdate()
                val keys = stmt.generatedKeys
                require(keys.next()) { "INSERT events produced no id" }
                keys.getLong(1)
            }
        }
    }

    /**
     * Read an `events` row by id. Returns null when the event was
     * soft-deleted or never existed.
     */
    fun findEvent(eventId: Long): EventRow? =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(EVENT_SELECT + "WHERE id = ? AND $ACTIVE_ROW_PREDICATE").use { stmt ->
                stmt.setLong(1, eventId)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.toEventRow() else null
            }
        }

    /**
     * Look up an event by title. Returns null when no active event
     * carries that title.
     */
    fun findEventByTitle(title: String): EventRow? =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(EVENT_SELECT + "WHERE title = ? AND $ACTIVE_ROW_PREDICATE").use { stmt ->
                stmt.setString(1, title)
                val rs = stmt.executeQuery()
                if (rs.next()) rs.toEventRow() else null
            }
        }

    private fun java.sql.ResultSet.toEventRow(): EventRow = EventRow(
        id = getLong("id"),
        title = getString("title"),
        description = getString("description"),
        location = getString("location"),
        approved = getBoolean("approved"),
        signUp = getBoolean("sign_up"),
        membersOnly = getBoolean("members_only"),
        committeeId = getObject("committee_id") as Long?,
        signUpLimit = getObject("sign_up_limit") as Int?,
    )

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
        val firstName: String = "Test",
        val lastName: String = "User",
    ) {
        val fullName: String get() = "$firstName $lastName"
    }

    data class SentEmail(
        val toEmail: String,
        val toName: String,
        val subject: String,
        val htmlContent: String,
    )

    data class MembershipRow(
        val id: Long,
        val userId: Long,
        val startDate: java.time.LocalDate,
        val endDate: java.time.LocalDate?,
        val type: String,
        val incasso: Boolean,
    )

    data class CommitteeRow(
        val id: Long,
        val name: String,
        val description: String,
    )

    data class CommitteeMemberRow(
        val userId: Long,
        val role: String?,
    )

    data class RegisteredUserRow(
        val id: Long,
        val username: String,
        val email: String,
        val enabled: Boolean,
        val discord: String?,
        val phoneNumber: String?,
    )

    data class JobExecutionRow(
        val id: Long,
        val jobType: String,
        val status: String,
        val attempts: Int,
        val queuedAt: java.time.Instant?,
        val startedAt: java.time.Instant?,
        val finishedAt: java.time.Instant?,
    )

    data class EventRow(
        val id: Long,
        val title: String,
        val description: String?,
        val location: String?,
        val approved: Boolean,
        val signUp: Boolean,
        val membersOnly: Boolean,
        val committeeId: Long?,
        val signUpLimit: Int?,
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
