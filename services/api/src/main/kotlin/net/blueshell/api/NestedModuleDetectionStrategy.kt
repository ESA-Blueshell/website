package net.blueshell.api

import org.springframework.modulith.core.ApplicationModuleDetectionStrategy
import org.springframework.modulith.core.JavaPackage
import java.util.stream.Stream

/**
 * Nominates the twenty application-module base packages, which sit two or three levels
 * below `net.blueshell.api` until the flattening moves them directly underneath it.
 *
 * Both the nested and the flat name of every module are listed, so a module that has
 * already moved and one that has not are both nominated.
 *
 * Each nominated package carries a `ModuleMetadata` class declaring the module's id, so the
 * module names are already the flat ones (`user`, `jobs`, `shared`) rather than the nested
 * package paths. Wired through `spring.modulith.detection-strategy`.
 */
// Temporary: deleted once the modules become direct sub-packages of the base package.
class NestedModuleDetectionStrategy : ApplicationModuleDetectionStrategy {

    override fun getModuleBasePackages(rootPackage: JavaPackage): Stream<JavaPackage> =
        rootPackage.getSubPackagesMatching { _, trailingName -> trailingName in MODULE_BASE_PACKAGES }

    companion object {

        /**
         * Module base packages, relative to `net.blueshell.api`.
         *
         * `jobs`, `sync` and `oidc` each span a second package today — `platform.integration.queue`,
         * `platform.integration.calendar` and `platform.web.oidc`. Modulith maps one package tree to
         * one module and rejects duplicate ids, so those three stay outside every module until the
         * flattening merges them into the package listed here.
         */
        val MODULE_BASE_PACKAGES: Set<String> = setOf(
            "domain.auth",
            "domain.blog",
            "domain.board",
            "domain.committee",
            "domain.contribution",
            "domain.esports",
            "domain.event",
            "domain.file",
            "domain.sponsor",
            "domain.survey",
            "domain.telemetry",
            "domain.user",
            "platform.integration.cohort",
            "platform.integration.contact",
            "platform.integration.email",
            "platform.integration.job",
            "platform.integration.sync",
            "platform.oidc",
            "infrastructure.security",
            "shared",
            // Flat names, matched once a module has been moved out from under
            // `domain` or `platform`. A name that matches nothing is simply not
            // nominated, so both layouts can be listed while the move is in progress.
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
