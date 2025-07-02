package net.blueshell.api.common.event;

public record EntityDeletedEvent<T>(T entity) {}
