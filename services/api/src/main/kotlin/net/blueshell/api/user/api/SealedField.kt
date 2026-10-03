package net.blueshell.api.user.api

/** One sealed value where it is stored: its row, the value and the context it is bound to. */
data class SealedValue(
    val id: Long,
    val sealed: String,
    val context: String,
)

/**
 * A sealed column the nightly rewrap keeps on the newest version of its key. A sealed field
 * registers by being a bean of this type.
 */
interface SealedField {
    /** What a value that could not be moved is named by, as in "address 12". */
    val name: String

    val key: String

    /** Every sealed value of the field, soft-deleted rows included. */
    fun sealedValues(): List<SealedValue>

    /** Swaps [was] for [sealed] on row [id]. False where the row was saved in between, which sealed it anew. */
    fun swap(
        id: Long,
        was: String,
        sealed: String,
    ): Boolean
}
