package net.blueshell.db;

public abstract class BaseController<S, M> {

    protected final S service;
    protected final M mapper;

    protected BaseController(S service, M mapper) {
        this.service = service;
        this.mapper = mapper;
    }
}
