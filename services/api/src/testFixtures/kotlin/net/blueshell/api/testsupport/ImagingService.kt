package net.blueshell.api.testsupport

import com.github.dockerjava.api.model.Capability
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path

/**
 * The imaging service every upload in a test is converted by, as the cluster runs it: read-only,
 * as nobody, with no capabilities.
 *
 * `IMAGING_URL` where CI has started one already. Otherwise built from its own Dockerfile and
 * started once for the whole run, by the first context that asks for it.
 */
object ImagingService {
    private const val PORT = 8080

    // Relative to services/api, where Gradle runs the tests.
    private val DOCKERFILE = Path.of("../imaging/Dockerfile")

    val url: String by lazy {
        System.getenv("IMAGING_URL")?.takeIf(String::isNotBlank)
            ?: container().let { "http://${it.host}:${it.getMappedPort(PORT)}" }
    }

    private fun container(): GenericContainer<*> =
        GenericContainer(ImageFromDockerfile("blueshell-imaging-it", false).withDockerfile(DOCKERFILE))
            .withExposedPorts(PORT)
            .withCreateContainerCmdModifier { command ->
                command.hostConfig
                    ?.withReadonlyRootfs(true)
                    ?.withCapDrop(Capability.ALL)
                    ?.withSecurityOpts(listOf("no-new-privileges"))
            }.waitingFor(Wait.forHttp("/healthz").forStatusCode(204))
            .also { it.start() }
}

/** Points `app.imaging.url` at [ImagingService], resolved when the converter first reads it. */
@TestConfiguration(proxyBeanMethods = false)
class ImagingServiceConfig {
    @Bean
    fun imagingUrl() = DynamicPropertyRegistrar { registry -> registry.add("app.imaging.url") { ImagingService.url } }
}
