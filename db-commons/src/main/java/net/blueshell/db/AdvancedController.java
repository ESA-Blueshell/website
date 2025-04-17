package net.blueshell.db;

import net.blueshell.common.identity.UserDetailsProvider;

public abstract class AdvancedController<S, AM, SM>  extends UserDetailsProvider {

    protected final S service;
    protected final AM advancedMapper;
    protected final SM simpleMapper;

    protected AdvancedController(S service, AM advancedMapper, SM simpleMapper) {
        this.service = service;
        this.advancedMapper = advancedMapper;
        this.simpleMapper = simpleMapper;
    }
}
