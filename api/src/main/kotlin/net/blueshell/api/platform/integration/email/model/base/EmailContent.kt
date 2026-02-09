package net.blueshell.api.platform.integration.email.model.base

import net.blueshell.api.feature.user.model.User

data class EmailContent(
    val recipient: User,
    val subject: String,
    val markdownContent: String,
    val senderName: String,
    val senderAddress: String,
    val replyTo: String
)