package net.blueshell.systemtests

import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import java.net.ConnectException
import java.sql.DriverManager
import java.util.UUID

/**
 * Shared HTTP + JDBC helper for system tests. Models the same shape as
 * personal-stack-2's TestHelper: the public api drives behaviour over
 * HTTP (register, login, etc.), and JDBC fills the gaps where the api
 * doesn't expose an admin path (granting roles, looking up tokens).
 *
 * This is the migration target for the in-process @Autowired
 * UserRepository / UserFactory / JwtTokenGenerator usages that the
 * existing FrontendSystemTestBase / OidcSystemTestBase still carry.
 */
object TestHelper {
    private const val API_RETRY_ATTEMPTS = 3
    private const val API_RETRY_DELAY_MS = 2_000L

    val apiBaseUrl: String get() = TestEnvironment.apiUrl

    private val dbUrl: String
        get() = System.getProperty("test.db.url", "jdbc:mariadb://localhost:3306/blueshell")
    private val dbUser: String
        get() = System.getProperty("test.db.user", "blueshell")
    private val dbPassword: String
        get() = System.getProperty("test.db.password", "ci-blueshell")

    fun givenApi(): RequestSpecification = given().relaxedHTTPSValidation()

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
     * Register a new user via the public POST /users endpoint and activate
     * their account by reading the activation token straight from the db.
     * Mirrors personal-stack-2's `registerAndConfirm` step-for-step.
     */
    fun registerAndActivate(
        username: String = "sys_${UUID.randomUUID().toString().take(8)}",
        password: String = "Test1234!",
        email: String = "$username@systemtest.example.com",
    ): RegisteredUser {
        retryOnConnectionFailure {
            givenApi()
                .baseUri(apiBaseUrl)
                .contentType(ContentType.JSON)
                .body(
                    """{"username":"$username","email":"$email","firstName":"Test","surname":"User","password":"$password"}""",
                ).`when`()
                .post("/users")
                .then()
                .statusCode(201)
        }

        val token = activationTokenFromDb(username)

        retryOnConnectionFailure {
            givenApi()
                .baseUri(apiBaseUrl)
                .contentType(ContentType.JSON)
                .body("""{"token":"$token","password":"$password"}""")
                .`when`()
                .post("/user/activate")
                .then()
                .statusCode(200)
        }

        return RegisteredUser(username, email, password)
    }

    /**
     * Hits the JDBC layer to elevate a user's role. The api's PUT
     * /users/{id}/roles requires an admin caller, so the first admin
     * has to be minted directly — matches the makeUserAdmin pattern
     * in personal-stack-2.
     */
    fun grantRole(username: String, role: String) {
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement("UPDATE user SET role = ? WHERE username = ?").use { stmt ->
                stmt.setString(1, role)
                stmt.setString(2, username)
                require(stmt.executeUpdate() == 1) { "Failed to set role=$role on username=$username" }
            }
        }
    }

    fun registerActivateAndPromote(
        role: String,
        username: String = "${role.lowercase()}_${UUID.randomUUID().toString().take(8)}",
        password: String = "Test1234!",
    ): RegisteredUser {
        val user = registerAndActivate(username = username, password = password)
        grantRole(user.username, role)
        return user
    }

    /**
     * Hits POST /auth and returns the Set-Cookie payload(s) so callers can
     * forward them into a Playwright BrowserContext or a follow-up HTTP
     * request. The api's session model uses a "login" cookie alongside
     * CSRF tokens — both are returned verbatim.
     */
    fun login(user: RegisteredUser): LoginCookies {
        val response = retryOnConnectionFailure {
            givenApi()
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
            login = response.cookie("login") ?: error("no login cookie in /auth response"),
            csrf = response.cookie("XSRF-TOKEN"),
        )
    }

    private fun activationTokenFromDb(username: String): String =
        DriverManager.getConnection(dbUrl, dbUser, dbPassword).use { conn ->
            conn.prepareStatement(
                """
                SELECT t.token FROM activation_token t
                JOIN user u ON u.id = t.user_id
                WHERE u.username = ?
                ORDER BY t.created_at DESC LIMIT 1
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, username)
                val rs = stmt.executeQuery()
                require(rs.next()) { "No activation token found for $username" }
                rs.getString("token")
            }
        }

    data class RegisteredUser(
        val username: String,
        val email: String,
        val password: String,
    )

    data class LoginCookies(
        val login: String,
        val csrf: String?,
    )
}
