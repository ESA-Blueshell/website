package net.blueshell.api.shared.job

/** Job types whose work is an outbound email, so they can share one SMTP connection. */
object EmailJobs {
    const val PREFIX = "email."

    fun isEmailJob(jobType: String): Boolean = jobType.startsWith(PREFIX)
}
