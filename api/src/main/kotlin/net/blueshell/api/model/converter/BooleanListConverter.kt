package net.blueshell.api.model.converter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.type.CollectionType
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Slf4j
@Converter
class BooleanListConverter : AttributeConverter<MutableList<Boolean?>?, String?> {
    val objectMapper = ObjectMapper()

    override fun convertToDatabaseColumn(attribute: MutableList<Boolean?>?): String? {
        try {
            return objectMapper.writeValueAsString(attribute)
        } catch (e: Exception) {
            throw RuntimeException("JSON conversion error", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): MutableList<Boolean?>? {
        if (dbData == null || dbData.isBlank() || "null".equals(dbData, ignoreCase = true)) {
            return ArrayList<Boolean?>()
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
        val MAPPER: ObjectMapper = JsonMapper.builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()
    }
}