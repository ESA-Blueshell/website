package net.blueshell.api.survey.persistence

import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.type.CollectionType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StringListConverter : AttributeConverter<MutableList<String?>?, String?> {
    val objectMapper: ObjectMapper = JsonMapper.builder().build()

    override fun convertToDatabaseColumn(attribute: MutableList<String?>?): String? {
        try {
            return objectMapper.writeValueAsString(attribute)
        } catch (e: JacksonException) {
            throw IllegalStateException("JSON conversion error", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): MutableList<String?>? {
        if (dbData.isNullOrBlank() || "null".equals(dbData, ignoreCase = true)) {
            return ArrayList()
        }
        try {
            val type: CollectionType? = MAPPER.typeFactory
                .constructCollectionType(MutableList::class.java, String::class.java)
            return MAPPER.readValue<MutableList<String?>?>(dbData, type)
        } catch (e: JacksonException) {
            throw IllegalStateException("Failed to deserialize JSON to List<FormQuestion>", e)
        }
    }

    companion object {
        val MAPPER: ObjectMapper = JsonMapper.builder().build()
    }
}
