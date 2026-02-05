package net.blueshell.api.controller.base

import net.blueshell.api.auth.IdentityProvider

abstract class AdvancedController<S, AM, SM> protected constructor(
    protected val service: S,
    protected val advancedMapper: AM,
    protected val simpleMapper: SM
) : IdentityProvider()
