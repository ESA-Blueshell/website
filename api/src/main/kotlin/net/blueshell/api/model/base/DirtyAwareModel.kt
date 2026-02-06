package net.blueshell.api.model.base

import jakarta.persistence.Transient
import java.util.*

abstract class DirtyAwareModel : AuditedAutoIdEntity() {
    @Transient
    var dirtyFields: Set<String> = emptySet()

    @Transient
    var dirty = false

    fun applyDirtyFields(fields: MutableSet<String>) {
        if (fields.isEmpty()) {
            this.dirtyFields = emptySet()
            this.dirty = false
        } else {
            // preserve order of discovery, expose as unmodifiable
            this.dirtyFields = Collections.unmodifiableSet(LinkedHashSet(fields))
            this.dirty = true
        }
    }
}