package net.blueshell.tools

import io.github.classgraph.ClassGraph
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    val parsed = parseArgs(args)
    val basePackage = parsed.basePackage ?: error("Missing required --base-package argument.")
    val dotOutputPath = parsed.dotOutputPath ?: error("Missing required --dot-output argument.")

    val scanResult = ClassGraph()
        .enableClassInfo()
        .enableInterClassDependencies()
        .acceptPackages(basePackage)
        .scan()

    scanResult.use {
        fun isTestClass(fqcn: String): Boolean {
            val simple = fqcn.substringAfterLast('.')
            // Adjust these rules to match your conventions
            return simple.contains("Test") ||
                    simple.contains("IT") ||
                    simple.contains("Spec") ||
                    fqcn.contains("test")
        }

        val allInternalClasses = it.allClasses

        // Filter out test classes so they won't appear as nodes or endpoints of edges
        val internalClasses = allInternalClasses
            .filterNot { ci -> isTestClass(ci.name) }

        val internalNames = internalClasses.map { it.name }.toSet()
        val edges = linkedSetOf<Pair<String, String>>()

        for (classInfo in internalClasses) {
            for (dependency in classInfo.classDependencies) {
                val dependencyName = dependency.name
                if (dependencyName != classInfo.name && dependencyName in internalNames) {
                    edges.add(classInfo.name to dependencyName)
                }
            }
        }

        val dotContents = buildString {
            appendLine("digraph \"blueshell-api\" {")
            appendLine("  rankdir=LR;")
            appendLine("  node [shape=box, fontname=\"Helvetica\"];")
            for (className in internalNames.sorted()) {
                appendLine("  \"${className}\";")
            }
            for ((from, to) in edges) {
                appendLine("  \"${from}\" -> \"${to}\";")
            }
            appendLine("}")
        }

        dotOutputPath.parent?.let { Files.createDirectories(it) }
        Files.writeString(dotOutputPath, dotContents)
    }

    parsed.svgOutputPath?.let { svgOutputPath ->
        renderSvg(dotOutputPath, svgOutputPath)
    }
}

private data class ParsedArgs(
    val dotOutputPath: Path?,
    val svgOutputPath: Path?,
    val basePackage: String?,
)

private fun parseArgs(args: Array<String>): ParsedArgs {
    var dotOutputPath: Path? = null
    var svgOutputPath: Path? = null
    var basePackage: String? = null
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--dot-output" -> {
                dotOutputPath = Path.of(args.getOrNull(i + 1) ?: "")
                i += 2
            }

            "--svg-output" -> {
                svgOutputPath = Path.of(args.getOrNull(i + 1) ?: "")
                i += 2
            }

            "--base-package" -> {
                basePackage = args.getOrNull(i + 1)
                i += 2
            }

            else -> error("Unknown argument: ${args[i]}")
        }
    }
    return ParsedArgs(dotOutputPath, svgOutputPath, basePackage)
}

private fun renderSvg(dotFile: Path, svgFile: Path) {
    try {
        svgFile.parent?.let { Files.createDirectories(it) }
        val process = ProcessBuilder(
            "dot",
            "-Tsvg",
            dotFile.toAbsolutePath().toString(),
            "-o",
            svgFile.toAbsolutePath().toString(),
        )
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            System.err.println("Graphviz 'dot' failed (exit $exitCode): $output")
        }
    } catch (ex: Exception) {
        System.err.println("Graphviz 'dot' not available; generated ${dotFile.toAbsolutePath()} only.")
    }
}
