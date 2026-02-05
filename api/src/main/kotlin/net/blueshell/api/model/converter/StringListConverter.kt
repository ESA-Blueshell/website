package net.blueshell.api.model.converter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.type.CollectionType
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StringListConverter : AttributeConverter<MutableList<String?>?, String?> {
    val objectMapper = ObjectMapper()

    override fun convertToDatabaseColumn(attribute: MutableList<String?>?): String? {
        try {
            return objectMapper.writeValueAsString(attribute)
        } catch (e: Exception) {
            throw RuntimeException("JSON conversion error", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): MutableList<String?>? {
        if (dbData.isNullOrBlank() || "null".equals(dbData, ignoreCase = true)) {
            return ArrayList<String?>()
        }
        try {
            val type: CollectionType? = MAPPER.typeFactory
                .constructCollectionType(MutableList::class.java, String::class.java)
            return MAPPER.readValue<MutableList<String?>?>(dbData, type)
        } catch (e: Exception) {
            throw RuntimeException("Failed to deserialize JSON to List<FormQuestion>", e)
        }
    }

    companion object {
        val MAPPER: ObjectMapper = JsonMapper.builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()
    }
}
