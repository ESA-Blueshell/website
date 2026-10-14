package net.blueshell.api.file.domain

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path

/**
 * The imaging service, built from its own Dockerfile and run as the cluster runs it: read-only,
 * with no capabilities. Started once for the whole run, by the first test that asks for it.
 */
object ImagingService {
    private const val PORT = 8080

    // Relative to services/api, where Gradle runs the tests.
    private val DOCKERFILE = Path.of("../imaging/Dockerfile")

    private val container: GenericContainer<*> by lazy {
        GenericContainer(ImageFromDockerfile("blueshell-imaging-it", false).withDockerfile(DOCKERFILE))
            .withExposedPorts(PORT)
            .withCreateContainerCmdModifier { command ->
                command.hostConfig
                    ?.withReadonlyRootfs(true)
                    ?.withCapDrop(com.github.dockerjava.api.model.Capability.ALL)
                    ?.withSecurityOpts(listOf("no-new-privileges"))
            }.waitingFor(Wait.forHttp("/healthz").forStatusCode(204))
            .also { it.start() }
    }

    val url: String get() = "http://${container.host}:${container.getMappedPort(PORT)}"
}
