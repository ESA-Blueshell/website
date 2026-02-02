package net.blueshell.api.base

import jakarta.persistence.Transient
import lombok.Getter
import java.util.*

abstract class DirtyAwareModel : BaseModel() {
    @Getter
    @Transient
    private var dirtyFields = mutableSetOf<String?>()

    @Transient
    @Getter
    private var dirty = false

    fun __applyDirtyFields(fields: MutableSet<String?>?) {
        if (fields == null || fields.isEmpty()) {
            this.dirtyFields = mutableSetOf<String?>()
            this.dirty = false
        } else {
            // preserve order of discovery, expose as unmodifiable
            this.dirtyFields = Collections.unmodifiableSet<String?>(LinkedHashSet<String?>(fields))
            this.dirty = true
        }
    }
}
