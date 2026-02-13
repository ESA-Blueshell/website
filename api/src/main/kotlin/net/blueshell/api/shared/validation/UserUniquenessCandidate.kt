package net.blueshell.api.shared.validation

interface UserUniquenessCandidate {
    val subjectId: Long?
    val username: String?
    val email: String?
    val discord: String?
    val phoneNumber: String?
}
