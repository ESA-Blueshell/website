package net.blueshell.api.user.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import net.blueshell.api.shared.enums.Role

/**
 * A set of roles as a comma-separated list of enum names, ordered by the enum.
 *
 * Ordered so that two writes of the same set produce the same string, which is what lets a
 * reader compare two rows by eye.
 */
@Converter
class RoleSetConverter : AttributeConverter<Set<Role>?, String?> {
    override fun convertToDatabaseColumn(attribute: Set<Role>?): String =
        attribute.orEmpty().sortedBy { it.ordinal }.joinToString(",") { it.name }

    override fun convertToEntityAttribute(dbData: String?): Set<Role> =
        dbData?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapTo(LinkedHashSet()) { Role.valueOf(it.trim()) }
            ?: emptySet()
}
