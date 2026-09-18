package net.blueshell.api.platform.web.dto

import java.io.Serial
import java.io.Serializable

abstract class BaseDTO : Serializable {
    companion object {
        @Serial
        private const val serialVersionUID: Long = 1L
    }
}
