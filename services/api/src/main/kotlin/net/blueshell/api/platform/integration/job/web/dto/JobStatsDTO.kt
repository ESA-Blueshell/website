package net.blueshell.api.platform.integration.job.web.dto

data class JobStatsDTO(
    // DB-derived — persistent across restarts
    val totalCount: Long,
    val successCount: Long,
    val failedCount: Long,
    val deadCount: Long,
    val queuedCount: Long,
    val runningCount: Long,

    // Micrometer runtime — since last app startup
    val deadSinceStartup: Double,
    val failedSinceStartup: Double,
    val recoveriesSinceStartup: Double,
    val avgSuccessDurationSeconds: Double,
)
