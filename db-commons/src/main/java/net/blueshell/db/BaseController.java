package net.blueshell.db;

import net.blueshell.common.identity.UserDetailsProvider;

public abstract class BaseController<S, M> extends UserDetailsProvider {

    protected final S service;
    protected final M mapper;

    protected BaseController(S service, M mapper) {
        this.service = service;
        this.mapper = mapper;
    }
}
