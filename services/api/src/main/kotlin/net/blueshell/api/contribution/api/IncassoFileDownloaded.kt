package net.blueshell.api.contribution.api

/** Somebody downloaded one of a run's files for ING, which holds [members] full account numbers. */
data class IncassoFileDownloaded(
    val downloadedBy: Long,
    val runId: Long,
    val part: Int,
    val parts: Int,
    val members: Int,
)
