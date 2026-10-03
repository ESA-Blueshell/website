package net.blueshell.api.contribution.api

/** A board member downloaded the PDF of the online mandate on [membershipId], which is [userId]'s. Never carries the IBAN. */
data class MandatePdfDownloaded(
    val userId: Long,
    val membershipId: Long,
    val downloadedBy: Long,
)
