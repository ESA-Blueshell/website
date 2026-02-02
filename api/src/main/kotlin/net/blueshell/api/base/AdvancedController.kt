package net.blueshell.api.base

abstract class AdvancedController<S, AM, SM> protected constructor(
    protected val service: S,
    protected val advancedMapper: AM,
    protected val simpleMapper: SM
) : IdentityProvider()
