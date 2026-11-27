package net.blueshell.api.vote.persistence

/** One pick as the count reads it: whose ballot it is on, where, and what it names. */
data class CastPick(
    val ballotId: Long,
    val position: Int,
    val candidateId: Long,
    val trackId: Long?,
)
