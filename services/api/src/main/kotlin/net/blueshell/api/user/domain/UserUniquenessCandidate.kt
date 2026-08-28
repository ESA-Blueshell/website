package net.blueshell.api.user.domain

interface UserUniquenessCandidate {
    val subjectId: Long?
    val username: String?
    val email: String?
    val discord: String?
    val phoneNumber: String?
}
