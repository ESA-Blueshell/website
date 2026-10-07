package net.blueshell.api.contribution.api

/** A board member downloaded the PDF of [userId]'s online mandate, known by its [reference]. Never carries the IBAN. */
data class MandatePdfDownloaded(
    val userId: Long,
    val reference: String,
    val downloadedBy: Long,
)
