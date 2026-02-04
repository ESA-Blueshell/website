package net.blueshell.api.base

import jakarta.persistence.Transient
import net.blueshell.api.base.entity.AuditedAutoIdEntity
import java.util.*

abstract class DirtyAwareModel : AuditedAutoIdEntity() {
    @Transient
    var dirtyFields: Set<String> = emptySet()
        private set

    @Transient
    var dirty = false
        private set

    fun __applyDirtyFields(fields: MutableSet<String>) {
        if (fields == null || fields.isEmpty()) {
            this.dirtyFields = emptySet()
            this.dirty = false
        } else {
            // preserve order of discovery, expose as unmodifiable
            this.dirtyFields = Collections.unmodifiableSet<String>(LinkedHashSet<String>(fields))
            this.dirty = true
        }
    }
}
