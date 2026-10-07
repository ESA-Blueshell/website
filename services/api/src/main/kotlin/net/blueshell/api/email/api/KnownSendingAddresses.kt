package net.blueshell.api.email.api

/** Whether an address an email may be sent from exists, for a module that names one. */
fun interface KnownSendingAddresses {
    fun exists(id: Long): Boolean
}
