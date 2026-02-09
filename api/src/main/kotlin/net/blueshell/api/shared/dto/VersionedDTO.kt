package net.blueshell.api.shared.dto

abstract class VersionedDTO : BaseDTO() {
    var version: Long = 0L
}
