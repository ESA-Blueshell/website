package net.blueshell.api.platform.integration.contact.job

data class SyncContactPayload(val userId: Long)

data class AddContactToListPayload(
    val userId: Long,
    val periodId: Long
)

data class RemoveContactFromListPayload(
    val userId: Long,
    val periodId: Long
)

data class CreateContributionPeriodListPayload(
    val periodId: Long
)
