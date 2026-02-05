package net.blueshell.api.controller.base

import net.blueshell.api.auth.IdentityProvider

abstract class BaseController<S, M>(
    protected val service: S,
    protected val mapper: M
) : IdentityProvider()