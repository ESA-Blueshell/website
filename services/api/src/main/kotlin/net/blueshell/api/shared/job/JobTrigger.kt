package net.blueshell.api.shared.job

import io.swagger.v3.oas.annotations.media.Schema

/** What queued a job, as the jobs page names it; who did it is the job's actor. */
@Schema(enumAsRef = true)
enum class JobTrigger {
    EVENT_CREATED,
    EVENT_UPDATED,
    EVENT_APPROVED,

    /** An edit by somebody not on the board sent the approved event back to it. */
    EVENT_SENT_BACK,
    EVENT_UNAPPROVED,
    EVENT_DELETED,
    SIGN_UPS_CHANGED,
    USER_CHANGED,
    USER_REMOVED,

    /** A cohort gained or lost a member. */
    MEMBERSHIP_CHANGED,

    /** Something a person, or the api on its own behalf, did on the site that sends mail or pushes data. */
    SITE_ACTION,

    /** The Discord posts' 08:00 run. */
    MORNING_RUN,

    /** Any other scheduled run: the daily contact sweep, the cohort check. */
    SCHEDULED_RUN,

    /** A running job that queues others, one per item it covers. */
    ANOTHER_JOB,

    /** The jobs page's trigger dialog. */
    BY_HAND,
}
