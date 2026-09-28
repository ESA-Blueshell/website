package net.blueshell.api.platform.config

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import java.io.File

/**
 * A dev-mode Vault for the tests that prove how the api reads it, with the policies the
 * bootstrap Job writes read from the Job's own script, so the two cannot drift.
 */
class VaultDevServer(
    network: Network? = null,
) : AutoCloseable {
    private val container: GenericContainer<*> =
        GenericContainer(IMAGE)
            .withEnv("VAULT_DEV_ROOT_TOKEN_ID", ROOT_TOKEN)
            .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:$PORT")
            .withEnv("SKIP_SETCAP", "true")
            .withExposedPorts(PORT)
            .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(200))
            .apply { network?.let(::withNetwork) }

    val uri: String get() = "http://${container.host}:${container.getMappedPort(PORT)}"

    fun start(): VaultDevServer = apply { container.start() }

    override fun close() = container.stop()

    fun put(
        path: String,
        vararg fields: String,
    ) {
        vault("kv", "put", path, *fields)
    }

    /** Writes the named policy exactly as bootstrap-auth.sh does and returns a token holding only it. */
    fun tokenFor(policy: String): String {
        container.copyFileToContainer(Transferable.of(bootstrapPolicy(policy)), "/tmp/$policy.hcl")
        vault("policy", "write", policy, "/tmp/$policy.hcl")
        return vault("token", "create", "-policy=$policy", "-field=token").trim()
    }

    fun vault(vararg args: String): String {
        val result =
            container.execInContainer(
                "env",
                "VAULT_ADDR=http://127.0.0.1:$PORT",
                "VAULT_TOKEN=$ROOT_TOKEN",
                "vault",
                *args,
            )
        check(result.exitCode == 0) { "vault ${args.joinToString(" ")} failed: ${result.stderr}" }
        return result.stdout
    }

    private fun bootstrapPolicy(name: String): String {
        val script = File(BOOTSTRAP_SCRIPT).readText()
        val start = script.indexOf("cat <<'EOF' >/tmp/$name.hcl\n")
        check(start >= 0) { "no $name policy in $BOOTSTRAP_SCRIPT" }
        return script.substring(start).substringAfter('\n').substringBefore("\nEOF\n") + "\n"
    }

    private companion object {
        private const val IMAGE = "hashicorp/vault:1.21.2"
        private const val PORT = 8200
        private const val ROOT_TOKEN = "root"
        private const val BOOTSTRAP_SCRIPT = "../../platform/cluster/flux/apps/data/vault/bootstrap-auth.sh"
    }
}
