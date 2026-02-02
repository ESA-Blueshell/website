package net.blueshell.api.base

abstract class BaseController<S, M>(
    protected val service: S,
    protected val mapper: M
) : IdentityProvider()