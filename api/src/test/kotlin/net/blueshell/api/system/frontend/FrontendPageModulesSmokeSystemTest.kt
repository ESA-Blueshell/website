package net.blueshell.api.system.frontend

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

@Tag("system")
class FrontendPageModulesSmokeSystemTest : FrontendSystemTestBase() {

    @Test
    fun `all frontend page modules can be imported`() {
        val modulePaths = discoverPageModulePaths()
        assertThat(modulePaths).isNotEmpty

        withPage { page ->
            page.navigate("$frontendUrl/")
            val failures = mutableListOf<String>()

            modulePaths.forEach { modulePath ->
                val result = page.evaluate(
                    """
                    async (path) => {
                      try {
                        const mod = await import(path)
                        return mod && mod.default ? "ok" : "missing-default-export"
                      } catch (error) {
                        return String(error?.message ?? error)
                      }
                    }
                    """.trimIndent(),
                    modulePath
                )?.toString()

                if (result != "ok") {
                    failures.add("$modulePath -> $result")
                }
            }

            assertThat(failures)
                .withFailMessage("Expected all frontend page modules to import successfully. Failures: %s", failures)
                .isEmpty()
        }
    }

    private fun discoverPageModulePaths(): List<String> {
        val pageRoot = listOf(
            Path.of("frontend/src/pages"),
            Path.of("../frontend/src/pages")
        ).firstOrNull { Files.exists(it) && Files.isDirectory(it) }
            ?: error("Could not locate frontend pages directory from system test working directory")

        Files.walk(pageRoot).use { paths ->
            return paths
                .filter { path ->
                    Files.isRegularFile(path) &&
                        path.toString().endsWith(".vue") &&
                        Files.size(path) > 0L
                }
                .map { file ->
                    val relative = pageRoot.relativize(file).toString().replace(File.separatorChar, '/')
                    "/src/pages/$relative"
                }
                .sorted()
                .toList()
        }
    }
}
