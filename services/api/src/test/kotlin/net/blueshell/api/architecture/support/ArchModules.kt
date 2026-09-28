package net.blueshell.api.architecture.support

import com.tngtech.archunit.core.domain.JavaClass

/**
 * Derives the architecture ADR-003 module a type belongs to from its package.
 *
 * Every direct sub-package of the base package is a module except `platform`, whose `config`, `web`
 * and `integration.mock` packages are the application root under ADR-003 rules 5 and 6. Types
 * directly under the base package belong to no module, since that is where global wiring lives, and
 * [moduleOf] returns null for them. The modules are read off the packages rather than listed, so a
 * new module cannot slip past the rules that ask for one.
 */
object ArchModules {
    const val BASE = "net.blueshell.api"

    private const val APPLICATION_ROOT = "platform"

    fun moduleOf(javaClass: JavaClass): String? = moduleOf(javaClass.packageName)

    fun moduleOf(packageName: String): String? {
        if (!packageName.startsWith("$BASE.")) return null
        return packageName.removePrefix("$BASE.").substringBefore('.').takeUnless { it == APPLICATION_ROOT }
    }

    /**
     * Whether a type in [originPackage] reaching [target] reads another module's data past its
     * services: [target] is a repository in some module's `persistence` package, and the origin is
     * outside that module. Api ADR-018.
     */
    fun reachesForeignRepository(
        originPackage: String,
        targetPackage: String,
        targetSimpleName: String,
    ): Boolean {
        val owner = moduleOf(targetPackage) ?: return false
        val inPersistence = targetPackage == "$BASE.$owner.persistence" || targetPackage.startsWith("$BASE.$owner.persistence.")
        val isRepository = inPersistence && targetSimpleName.endsWith("Repository")
        return isRepository && moduleOf(originPackage) != owner
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
