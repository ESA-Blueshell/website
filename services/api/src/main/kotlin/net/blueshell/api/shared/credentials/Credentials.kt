package net.blueshell.api.shared.credentials

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata

/** What each vendor needs set before its real client replaces the stand-in. */
object Credentials {
    const val BREVO = "brevo.apiKey"
    const val GOOGLE_CALENDAR_ID = "google.calendar.id"
    const val GOOGLE_CALENDAR_KEY = "google.calendar.serviceAccountJson"
    const val SMTP_HOST = "spring.mail.host"
    const val IMAP_HOST = "email.bounce.imap.host"
    const val IMAP_USERNAME = "email.bounce.imap.username"
    const val IMAP_PASSWORD = "email.bounce.imap.password"
    const val DISCORD_BOT = "discord.botToken"
}

/**
 * A real vendor client: registered where every one of [properties] is set to something other
 * than blank. Its in-memory stand-in carries [WhenCredentialsMissing] with the same properties, so
 * exactly one of the two exists whatever the profile.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(CredentialsSet::class)
annotation class WhenCredentialsSet(
    vararg val properties: String,
)

/** A vendor's in-memory stand-in: registered where any of [properties] is blank or absent. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(CredentialsMissing::class)
annotation class WhenCredentialsMissing(
    vararg val properties: String,
)

internal class CredentialsSet : Condition {
    override fun matches(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): Boolean = allSet(context, metadata, WhenCredentialsSet::class.java.name)
}

internal class CredentialsMissing : Condition {
    override fun matches(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): Boolean = !allSet(context, metadata, WhenCredentialsMissing::class.java.name)
}

private fun allSet(
    context: ConditionContext,
    metadata: AnnotatedTypeMetadata,
    annotation: String,
): Boolean {
    @Suppress("UNCHECKED_CAST")
    val properties = metadata.getAnnotationAttributes(annotation)?.get("properties") as Array<String>
    return properties.all { !context.environment.getProperty(it).isNullOrBlank() }
}
