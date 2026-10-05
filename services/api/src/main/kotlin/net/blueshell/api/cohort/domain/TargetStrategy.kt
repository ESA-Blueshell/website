package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.shared.enums.TargetSystem

/** Which system a strategy speaks for and the kind of cohort it holds there. The words are the screens'. */
data class TargetDescriptor(
    val system: TargetSystem,
    val kind: TargetKind,
)

data class ExternalTarget(
    val system: TargetSystem,
    val externalId: String,
    val kind: TargetKind,
    val label: String,
    val folderLabel: String? = null,
    val memberCount: Long? = null,
    val linkedTargetId: Long? = null,
    /**
     * Where this target sits on its system, from the outside in: `[Brevo, Periods]` for a
     * list in a folder, `[Brevo]` for one at the top level, and as many entries as a system
     * that nests more deeply has to report.
     *
     * Read rather than derived from [folderLabel] so a system with a real hierarchy needs no
     * migration to describe itself — and so one with none costs nothing to describe.
     */
    val path: List<String> = emptyList(),
)

data class ExternalMember(
    val externalUserId: String,
    val label: String?,
)

/** How a system knows the site's people: the id each has there, and who an id there belongs to. */
interface MemberIdentity {
    /** The id each of [userIds] has on the system, for those who have one. */
    fun memberIds(userIds: Set<Long>): Map<Long, String>

    /** The account behind each of [externalUserIds], for those an account holds. */
    fun ownersOf(externalUserIds: Set<String>): Map<String, Long>

    /**
     * Whether a user without an id there is given one by the site, as Brevo is given a contact.
     * Where not, they are unreachable: counted apart from drift and never pushed.
     */
    val makesMemberIds: Boolean

    /** Sets off making [userId]'s id on the system; only called where [makesMemberIds]. */
    fun makeMemberId(userId: Long)
}

/**
 * The one port over a cohort's external target: its catalogue, its folders, and who is on it.
 *
 * Ids are [String] so a Discord snowflake or a Google group address sits beside Brevo's numeric
 * list id; an adapter that needs another shape converts at its own edge and nowhere else.
 */
interface TargetStrategy : MemberIdentity {
    val descriptor: TargetDescriptor
    val system: TargetSystem get() = descriptor.system

    /**
     * A target known only by its id. The member, move and delete calls key on the id alone, so a
     * caller holding one writes without reading the catalogue first.
     */
    fun handle(externalId: String): ExternalTarget = ExternalTarget(system, externalId, descriptor.kind, externalId)

    fun catalog(query: String?): List<ExternalTarget> = emptyList()

    fun resolve(externalId: String): ExternalTarget? = catalog(externalId).firstOrNull { it.externalId == externalId }

    fun members(external: ExternalTarget): List<ExternalMember>

    /**
     * Whether the system can be reached now. A target on one that cannot is absent: its writes and
     * reconciles skip rather than fail, and its catalogue is empty.
     */
    fun available(): Boolean = true

    fun add(
        external: ExternalTarget,
        externalUserId: String,
    )

    fun remove(
        external: ExternalTarget,
        externalUserId: String,
    )

    fun create(
        label: String,
        folder: String?,
    ): ExternalTarget

    /**
     * Every folder the system has, whether or not anything is filed in it.
     *
     * Read rather than inferred from the targets: a folder holding nothing is invisible to
     * the catalogue, and an empty folder is exactly where a target is most likely headed.
     */
    fun folders(): List<String> = emptyList()

    /** The system's folders as things of their own, where it has folders that can be merged or removed. */
    val folderKeeper: FolderKeeper? get() = null

    /**
     * File a target under another folder, and answer with where it ended up.
     *
     * A system that cannot move one keeps this default and refuses.
     */
    fun move(
        external: ExternalTarget,
        folder: String,
    ): ExternalTarget = throw UnsupportedOperationException("$system cannot move a target between folders")

    /** Give a target another name. */
    fun rename(
        external: ExternalTarget,
        name: String,
    ): ExternalTarget

    /** Make a folder by name, or find the one already called that; answers every folder. */
    fun createFolder(name: String): List<String>

    fun delete(external: ExternalTarget)
}
