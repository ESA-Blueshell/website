package net.blueshell.db;

public abstract class AdvancedController<S, AM, SM> {

    protected final S service;
    protected final AM advancedMapper;
    protected final SM simpleMapper;

    protected AdvancedController(S service, AM advancedMapper, SM simpleMapper) {
        this.service = service;
        this.advancedMapper = advancedMapper;
        this.simpleMapper = simpleMapper;
    }
}
