package net.blueshell.api.domain.survey.persistence.converter

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.type.CollectionType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class BooleanListConverter : AttributeConverter<MutableList<Boolean?>?, String?> {
    val objectMapper: ObjectMapper = JsonMapper.builder().build()

    override fun convertToDatabaseColumn(attribute: MutableList<Boolean?>?): String? {
        try {
            return objectMapper.writeValueAsString(attribute)
        } catch (e: Exception) {
            throw RuntimeException("JSON conversion error", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): MutableList<Boolean?>? {
        if (dbData.isNullOrBlank() || "null".equals(dbData, ignoreCase = true)) {
            return ArrayList()
        }
        try {
            val type: CollectionType? = MAPPER.typeFactory
                .constructCollectionType(MutableList::class.java, Boolean::class.java)
            return MAPPER.readValue<MutableList<Boolean?>?>(dbData, type)
        } catch (e: Exception) {
            throw RuntimeException("Failed to deserialize JSON to List<FormQuestion>", e)
        }
    }

    companion object {
        val MAPPER: ObjectMapper = JsonMapper.builder().build()
    }
}
