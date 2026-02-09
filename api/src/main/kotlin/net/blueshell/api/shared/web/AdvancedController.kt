package net.blueshell.api.shared.web

import net.blueshell.api.auth.security.IdentityProvider

abstract class AdvancedController<S, AM, SM> protected constructor(
    protected val service: S,
    protected val advancedMapper: AM,
    protected val simpleMapper: SM
) : IdentityProvider()
