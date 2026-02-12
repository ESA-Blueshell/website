package net.blueshell.api.shared.web

abstract class AdvancedController<S> protected constructor(
    protected val service: S
)
