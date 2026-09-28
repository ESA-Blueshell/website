package net.blueshell.api.board.domain

import java.time.LocalDate

/** What a board write says about a board, whether it makes one or edits one. */
data class BoardInput(
    val number: Int,
    val name: String?,
    val candidate: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val photo: String?,
    val cheer: String? = null,
    val accent: String? = null,
    val description: String? = null,
)

/** What a write says about a place on a board. The account it belongs to is said separately. */
data class BoardMemberInput(
    val role: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val displayName: String? = null,
    val nickname: String? = null,
    val description: String? = null,
    val portrait: String? = null,
)
