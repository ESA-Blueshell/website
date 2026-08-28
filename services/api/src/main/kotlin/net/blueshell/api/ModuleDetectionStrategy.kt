package net.blueshell.api

import org.springframework.modulith.core.ApplicationModuleDetectionStrategy
import org.springframework.modulith.core.JavaPackage
import java.util.stream.Stream

/**
 * Nominates the twenty application-module base packages.
 *
 * The modules are direct sub-packages of `net.blueshell.api`, which is what Modulith's
 * default strategy detects — but the default would also nominate `platform`, whose
 * `config`, `web` and `integration.mock` packages are the application root under
 * architecture ADR-003 rules 5 and 6 rather than a module. Naming the twenty explicitly
 * keeps that package out and keeps the module list reviewable.
 *
 * Wired through `spring.modulith.detection-strategy`.
 */
class ModuleDetectionStrategy : ApplicationModuleDetectionStrategy {

    override fun getModuleBasePackages(rootPackage: JavaPackage): Stream<JavaPackage> =
        rootPackage.getSubPackagesMatching { _, trailingName -> trailingName in MODULE_BASE_PACKAGES }

    companion object {

        /** Module base packages, relative to `net.blueshell.api`. */
        val MODULE_BASE_PACKAGES: Set<String> = setOf(
            "shared",
            "auth",
            "blog",
            "board",
            "committee",
            "contribution",
            "esports",
            "event",
            "file",
            "sponsor",
            "survey",
            "telemetry",
            "user",
            "cohort",
            "contact",
            "email",
            "jobs",
            "sync",
            "oidc",
            "security",
        )
    }
}
