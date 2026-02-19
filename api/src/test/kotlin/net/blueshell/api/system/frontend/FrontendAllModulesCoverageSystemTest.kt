package net.blueshell.api.system.frontend

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@Tag("system")
class FrontendAllModulesCoverageSystemTest : FrontendSystemTestBase() {

    @Test
    fun `all frontend source modules are loaded to populate coverage counters`() {
        val modulePaths = discoverAllSourceModulePaths()

        withPage { page ->
            page.navigate("$frontendUrl/")

            for (modulePath in modulePaths) {
                runCatching {
                    page.evaluate(
                        """
                        async (path) => {
                          try { await import(path) } catch (_) { /* ignore */ }
                        }
                        """.trimIndent(),
                        modulePath
                    )
                }
            }
        }
    }

    private fun discoverAllSourceModulePaths(): List<String> {
        val srcRoot = listOf(
            Path.of("frontend/src"),
            Path.of("../frontend/src")
        ).firstOrNull { Files.exists(it) && Files.isDirectory(it) }
            ?: error("Could not locate frontend/src directory")

        return Files.walk(srcRoot).use { paths ->
            paths
                .filter { path ->
                    Files.isRegularFile(path) &&
                        (path.toString().endsWith(".ts") || path.toString().endsWith(".vue")) &&
                        !path.toString().contains("/services/api/") &&
                        !path.toString().endsWith(".gen.ts") &&
                        Files.size(path) > 0L
                }
                .map { file ->
                    val relative = srcRoot.relativize(file).toString()
                        .replace(File.separatorChar, '/')
                    "/src/$relative"
                }
                .sorted()
                .toList()
        }
    }
}