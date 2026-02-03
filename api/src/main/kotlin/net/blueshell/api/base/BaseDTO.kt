package net.blueshell.api.base

import java.io.Serializable
import java.time.Instant

abstract class BaseDTO : Serializable {
    open var id: Long? = null
    open var deletedAt: Instant? = null
    open var createdAt: Instant? = null
    open var updatedAt: Instant? = null
    open var version: Long? = null
}
