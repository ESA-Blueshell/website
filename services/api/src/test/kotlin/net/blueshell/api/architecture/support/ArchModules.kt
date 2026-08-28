package net.blueshell.api.architecture.support

import com.tngtech.archunit.core.domain.JavaClass

/**
 * Derives the architecture ADR-003 module a type belongs to from its package.
 *
 * Modules are not the direct sub-packages of the base package — `domain` and
 * `platform` are grouping levels that ADR-003 sequences away last, so the module
 * is one or two segments deeper. A handful of packages were split by role rather
 * than by feature and still map onto a single module: `job` plus `queue` are both
 * `jobs`, and `sync` plus `calendar` are both `sync`.
 *
 * Types directly under the base package belong to no module — that is where
 * global wiring lives — and [moduleOf] returns null for them.
 */
object ArchModules {

    const val BASE = "net.blueshell.api"

    /** Packages split by role that ADR-003 folds back into one module. */
    private val INTEGRATION_ALIASES = mapOf(
        "job" to "jobs",
        "queue" to "jobs",
        "calendar" to "sync",
        "sync" to "sync",
    )

    /**
     * Modules already moved to a direct sub-package of the base package. Listed so the
     * derivation works while some modules are flattened and others are not.
     */
    private val FLAT_MODULES = setOf(
        "auth", "blog", "board", "committee", "contribution", "esports", "event", "file",
        "sponsor", "survey", "telemetry", "user", "cohort", "contact", "email", "jobs",
        "sync", "oidc", "security", "shared",
    )

    fun moduleOf(javaClass: JavaClass): String? = moduleOf(javaClass.packageName)

    fun moduleOf(packageName: String): String? {
        if (packageName != BASE && !packageName.startsWith("$BASE.")) return null
        val segments = packageName.removePrefix(BASE).removePrefix(".").split(".").filter { it.isNotEmpty() }
        return when {
            segments.isEmpty() -> null
            segments[0] in FLAT_MODULES -> segments[0]
            segments[0] == "domain" -> segments.getOrNull(1)
            segments[0] == "shared" -> "shared"
            segments[0] == "infrastructure" -> segments.getOrNull(1)
            segments[0] == "platform" && segments.getOrNull(1) == "integration" ->
                segments.getOrNull(2)?.let { INTEGRATION_ALIASES[it] ?: it }
            segments[0] == "platform" -> segments.getOrNull(1)
            else -> null
        }
    }

    /**
     * A module's web package holds controllers, request/response types and their
     * mappers. `platform/web` is a module whose whole body is web, so the check is
     * on any `web` segment rather than on position relative to the module root.
     */
    fun isWebPackage(packageName: String): Boolean {
        if (!packageName.startsWith("$BASE.")) return false
        return packageName.removePrefix("$BASE.").split(".").any { it == "web" }
    }

    /**
     * The `shared` package a type sits in, at the granularity ADR-003's fan-in
     * table uses: the deepest package that actually holds types, so
     * `shared/dto/bulk` counts separately from `shared/dto`.
     */
    fun sharedPackageOf(javaClass: JavaClass): String? {
        val packageName = javaClass.packageName
        if (!packageName.startsWith("$BASE.shared.")) return null
        return packageName.removePrefix("$BASE.").replace('.', '/')
    }
}
