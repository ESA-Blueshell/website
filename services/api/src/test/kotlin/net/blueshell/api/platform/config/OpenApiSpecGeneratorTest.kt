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
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.util.TreeMap

/**
 * Generates the OpenAPI specification via in-memory H2 database (no MariaDB required).
 *
 * The specification is derived purely from Spring controllers and DTOs — the database
 * is only needed to boot the application context. Using H2 with Flyway disabled and
 * hibernate ddl-auto=create-drop allows the app to bootstrap without a real database.
 *
 * The test GETs the /v3/api-docs endpoint with MockMvc configured to return server
 * URLs as http://localhost:8080, then writes the response to build/openapi.raw.yaml as
 * block-style YAML with its keys sorted. The Gradle dumpOpenApiSpec task copies that to the
 * committed services/api/openapi.yaml.
 *
 * YAML with a line per value rather than minified JSON, because the committed spec is a
 * generated file that every branch touching a controller regenerates. One line per value
 * means two branches adding an endpoint each conflict only where they actually disagree,
 * where a single-line document conflicts on the whole file every time.
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

        // Keys sorted so the diff is about what the api changed, not about the order springdoc
        // happened to walk the beans in.
        val sorted = sortKeys(ObjectMapper().readValue(rawSpec, Any::class.java))

        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            // Long descriptions stay on their line rather than being folded, so a reworded
            // sentence is a one-line diff.
            width = Int.MAX_VALUE
            splitLines = false
        }
        val yaml = Yaml(options).dump(sorted)

        // gradle's layout.buildDirectory resolves relative to the current project, so the
        // dumpOpenApiSpec task reads from here.
        val buildDir = File(System.getProperty("user.dir")).resolve("build")
        buildDir.mkdirs()
        val outputFile = File(buildDir, "openapi.raw.yaml")
        outputFile.writeText(yaml)

        println("OpenAPI raw spec written to ${outputFile.absolutePath}")
    }

    /** Recursively sorts every object's keys; arrays keep the order the api gave them. */
    private fun sortKeys(node: Any?): Any? = when (node) {
        is Map<*, *> -> TreeMap<String, Any?>().apply {
            node.forEach { (key, value) -> put(key as String, sortKeys(value)) }
        }
        is List<*> -> node.map { sortKeys(it) }
        else -> node
    }
}
