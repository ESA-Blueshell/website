package net.blueshell.api.shared.dto

abstract class VersionedDTO(
    var version: Long? = null
) : BaseDTO()
