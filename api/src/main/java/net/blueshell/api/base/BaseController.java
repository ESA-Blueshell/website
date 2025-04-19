package net.blueshell.api.base;

public abstract class BaseController<S, M> extends IdentityProvider {

    protected final S service;
    protected final M mapper;

    protected BaseController(S service, M mapper) {
        this.service = service;
        this.mapper = mapper;
    }
}
