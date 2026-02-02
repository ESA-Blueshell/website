package net.blueshell.api.base;

import jakarta.persistence.Transient;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class DirtyAwareModel extends BaseModel {
    @Getter
    @Transient
    private Set<String> dirtyFields = Collections.emptySet();

    @Transient
    @Getter
    private boolean dirty;

    public void __applyDirtyFields(Set<String> fields) {
        if (fields == null || fields.isEmpty()) {
            this.dirtyFields = Collections.emptySet();
            this.dirty = false;
        } else {
            // preserve order of discovery, expose as unmodifiable
            this.dirtyFields = Collections.unmodifiableSet(new LinkedHashSet<>(fields));
            this.dirty = true;
        }
    }
}
