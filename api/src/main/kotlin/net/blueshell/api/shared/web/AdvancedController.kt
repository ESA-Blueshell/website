package net.blueshell.api.shared.web

import net.blueshell.api.domain.auth.security.IdentityProvider

abstract class AdvancedController<S> protected constructor(
    protected val service: S
) : IdentityProvider()
