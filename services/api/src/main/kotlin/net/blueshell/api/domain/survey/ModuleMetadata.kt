package net.blueshell.api.domain.survey

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Questionnaires that hang off something else: questions, the answer shape each question type
 * allows, and the answers given.
 *
 * Validating an answer against its question is this module's rule; the modules that attach a
 * survey — event sign-ups today — reference it rather than owning it.
 */
@PackageInfo
@ApplicationModule(
    id = "survey",
    allowedDependencies = [
        // Open kernel.
        "shared",
    ],
)
class ModuleMetadata
