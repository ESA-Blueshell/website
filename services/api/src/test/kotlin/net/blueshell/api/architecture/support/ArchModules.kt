package net.blueshell.api.architecture.support

import com.tngtech.archunit.core.domain.JavaClass

/**
 * Derives the architecture ADR-003 module a type belongs to from its package.
 *
 * Every module is a direct sub-package of the base package. `platform` is not one:
 * its `config`, `web` and `integration.mock` packages are the application root under
 * ADR-003 rules 5 and 6.
 *
 * Types directly under the base package belong to no module — that is where
 * global wiring lives — and [moduleOf] returns null for them.
 */
object ArchModules {

    const val BASE = "net.blueshell.api"

    /**
     * The twenty modules, each a direct sub-package of the base package.
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
            // platform/config, platform/web and platform/integration/mock are the
            // application root under ADR-003 rules 5 and 6, not a module.
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
