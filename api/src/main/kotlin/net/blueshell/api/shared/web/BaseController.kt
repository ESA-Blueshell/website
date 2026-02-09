package net.blueshell.api.shared.web

import net.blueshell.api.auth.security.IdentityProvider

abstract class BaseController<S>(
    protected val service: S
) : IdentityProvider()
