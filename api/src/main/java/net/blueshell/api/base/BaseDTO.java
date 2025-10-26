package net.blueshell.api.base;

import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
public abstract class BaseDTO implements Serializable {
    private Long id;
    private Instant deletedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
