package net.blueshell.api.common.event.jpa;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public record ValueChange(Object before, Object after) {
}
