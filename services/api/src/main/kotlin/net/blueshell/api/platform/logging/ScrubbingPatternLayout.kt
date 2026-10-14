package net.blueshell.api.platform.logging

import ch.qos.logback.classic.PatternLayout
import ch.qos.logback.classic.spi.ILoggingEvent
import net.blueshell.api.shared.util.PersonalDetails

/**
 * The console's layout, scrubbing each finished line, its stack trace included, of personal details
 * and of any password carried as a `password=` parameter, as a JDBC URL carries the database login.
 *
 * It scrubs the line rather than a conversion word because logback appends a stack trace a pattern
 * leaves out with a converter of its own, which no conversion rule reaches.
 */
class ScrubbingPatternLayout : PatternLayout() {
    override fun doLayout(event: ILoggingEvent): String =
        passwordParameter.replace(PersonalDetails.scrub(super.doLayout(event)), "$1[secret]")

    private companion object {
        private val passwordParameter = Regex("""(?i)(\bpassword=)[^&;\s\]]+""")
    }
}
