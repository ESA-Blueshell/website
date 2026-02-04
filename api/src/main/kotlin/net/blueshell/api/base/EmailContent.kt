package net.blueshell.api.base

import net.blueshell.api.model.User


data class EmailContent(
    val recipient: User,
    val subject: String,
    val markdownContent: String,
    val senderName: String,
    val senderAddress: String,
    val replyTo: String
)
