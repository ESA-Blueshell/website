package net.blueshell.api.shared.web

import net.blueshell.api.feature.auth.security.IdentityProvider

abstract class BaseController<S, M>(
    protected val service: S,
    protected val mapper: M
) : IdentityProvider()