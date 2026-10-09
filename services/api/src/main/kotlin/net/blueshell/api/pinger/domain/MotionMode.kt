package net.blueshell.api.pinger.domain

import com.fasterxml.jackson.annotation.JsonProperty

/** How a placement moves across the canvas. Stored and sent lowercase. */
enum class MotionMode {
    /** Stays at its origin. */
    @JsonProperty("static")
    STATIC,

    /** Travels at its velocity and reflects off the canvas edges, DVD-logo style. */
    @JsonProperty("bounce")
    BOUNCE,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun ofWire(value: String): MotionMode = valueOf(value.uppercase())
    }
}
