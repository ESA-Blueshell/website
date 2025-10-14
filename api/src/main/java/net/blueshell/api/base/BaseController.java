package net.blueshell.api.base;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class BaseController<S, M> extends IdentityProvider {

    protected final S service;
    protected final M mapper;
}
