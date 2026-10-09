package net.blueshell.api.pinger.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import net.blueshell.api.pinger.domain.MotionMode

/** Stores a [MotionMode] as its lowercase wire name, matching the column default 'static'. */
@Converter
class MotionModeConverter : AttributeConverter<MotionMode, String> {
    override fun convertToDatabaseColumn(attribute: MotionMode): String = attribute.wire

    override fun convertToEntityAttribute(dbData: String): MotionMode = MotionMode.ofWire(dbData)
}
