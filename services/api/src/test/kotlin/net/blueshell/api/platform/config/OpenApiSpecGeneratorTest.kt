package net.blueshell.api.platform.config

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File

/**
 * Generates the OpenAPI specification via in-memory H2 database (no MariaDB required).
 *
 * The specification is derived purely from Spring controllers and DTOs — the database
 * is only needed to boot the application context. Using H2 with Flyway disabled and
 * hibernate ddl-auto=create-drop allows the app to bootstrap without a real database.
 *
 * The test GETs the /v3/api-docs endpoint with MockMvc configured to return server
 * URLs as http://localhost:8080, then writes the raw JSON response to build/openapi.raw.json.
 * The Gradle dumpOpenApiSpec task then normalizes this via `jq -S -c` into the committed
 * services/api/openapi.json.
 *
 * This test is tagged "openapi-gen" and excluded from the normal test task, so it only
 * runs when explicitly invoked via the dumpOpenApiSpec task.
 */
@SpringBootTest
@ActiveProfiles("test", "openapi-gen")
@Tag("openapi-gen")
class OpenApiSpecGeneratorTest {

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    @Autowired
    private lateinit var springSecurityFilterChain: FilterChainProxy

    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setupMvc() {
        val builder = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        builder.addFilters<DefaultMockMvcBuilder>(springSecurityFilterChain)
        builder.defaultRequest<DefaultMockMvcBuilder>(get("/").with(csrf().asHeader()))
        mvc = builder.build()
    }

    @Test
    fun `generates openapi spec via in-memory h2`() {
        // GET /v3/api-docs with server info set to http://localhost:8080.
        // MockMvc defaults to localhost:80; override to 8080 so the spec's
        // servers URL matches what the production app would expose.
        val response = mvc.perform(
            get("/v3/api-docs")
                .with { req ->
                    req.serverName = "localhost"
                    req.serverPort = 8080
                    req.scheme = "http"
                    req
                }
        )
            .andExpect(status().isOk)
            .andReturn()

        val rawSpec = response.response.contentAsString
        // Write to the build directory; gradle's layout.buildDirectory resolves relative to the current project.
        // The dumpOpenApiSpec task will read from here.
        val outputDir = File(System.getProperty("java.io.tmpdir")).resolve("openapi-spec-gen-${System.currentTimeMillis()}")
        outputDir.mkdirs()

        // Also write to the standard Gradle build directory path for this module.
        val buildDir = File(System.getProperty("user.dir")).resolve("build")
        buildDir.mkdirs()
        val outputFile = File(buildDir, "openapi.raw.json")
        outputFile.writeText(rawSpec)

        println("OpenAPI raw spec written to ${outputFile.absolutePath}")
    }
}
