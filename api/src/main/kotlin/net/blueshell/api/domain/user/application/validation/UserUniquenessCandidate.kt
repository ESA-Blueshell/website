package net.blueshell.api.domain.user.application.validation

interface UserUniquenessCandidate {
    val subjectId: Long?
    val username: String?
    val email: String?
    val discord: String?
    val phoneNumber: String?
}
