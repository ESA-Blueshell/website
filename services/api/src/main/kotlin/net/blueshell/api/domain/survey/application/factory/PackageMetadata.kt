package net.blueshell.api.domain.survey.application.factory

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * Construction of a survey from its data shape, so a module embedding a survey does not
 * assemble the aggregate itself.
 */
@PackageInfo
@NamedInterface("api")
class PackageMetadata
