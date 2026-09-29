package net.blueshell.api.committee.api

import net.blueshell.api.committee.domain.CommitteeMemberData
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.committee.persistence.CommitteeRepository
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.game.api.GameService
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.model.addressOf
import net.blueshell.api.user.api.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommitteeService
    @Autowired
    constructor(
        private val repository: CommitteeRepository,
        private val userService: UserService,
        private val trackedEvents: TrackedEventPublisher,
        private val pictures: StoredPictures,
        private val games: GameService,
    ) {
        @Transactional(readOnly = true)
        fun findAll(): List<Committee> = repository.findAll()

        @Transactional(readOnly = true)
        fun findById(id: Long): Committee = repository.findById(id).orElseThrow { CommitteeNotFound(id) }

        /** A committee as given, members and all: they are saved with it. */
        @Transactional
        fun create(committee: Committee): Committee =
            repository.saveAndFlush(committee).also { saved ->
                trackedEvents.publish { actor -> CommitteeCreated(saved.id!!, actor = actor) }
            }

        @Transactional
        fun deleteById(id: Long) = repository.delete(findById(id))

        /**
         * The committee whose page answers to [address], whatever its case. One the release before
         * this one added has no address stored yet, and answers to the one its name makes.
         */
        @Transactional(readOnly = true)
        fun findByAddress(address: String): Committee {
            val asked = address.trim().lowercase()
            return repository.findBySlug(asked)
                ?: repository.findAllBySlugIsNull().firstOrNull { it.slug == asked }
                ?: throw UnknownCommitteeAddress(address)
        }

        @Transactional
        fun createWithMembers(
            name: String,
            description: String,
            members: List<CommitteeMemberData>,
            page: CommitteePage = CommitteePage(),
        ): Committee {
            val committee = Committee(name = name, description = description)
            applyPage(committee, page)
            reconcileMembers(committee, members)
            val saved = repository.saveAndFlush(committee)
            trackedEvents.publish { actor -> CommitteeCreated(saved.id!!, actor = actor) }
            publishMembershipChanges(saved.id!!, saved.members.map { it.userId }.toSet())
            return saved
        }

        @Transactional
        fun updateWithMembers(
            id: Long,
            name: String,
            description: String,
            members: List<CommitteeMemberData>,
            version: Long?,
            page: CommitteePage = CommitteePage(),
        ): Committee {
            val committee = findById(id)
            version?.let(committee::requireVersion)
            val previousMembers = committee.members.associate { it.userId to it.role }

            committee.name = name
            committee.description = description
            applyPage(committee, page)
            reconcileMembers(committee, members)

            val saved = repository.saveAndFlush(committee)
            val currentMembers = saved.members.associate { it.userId to it.role }
            val changedUserIds = changedUserIds(previousMembers, currentMembers)
            publishMembershipChanges(saved.id!!, changedUserIds)
            return saved
        }

        /**
         * What a committee's own members may change: its description, its pictures and its games.
         * The name, the address and the members stay the board's.
         */
        @Transactional
        fun updateOwnPage(
            id: Long,
            description: String,
            banner: String?,
            icon: String?,
            gameCodes: List<String>?,
            version: Long?,
        ): Committee {
            val committee = findById(id)
            version?.let(committee::requireVersion)
            committee.description = description
            committee.banner = pictures.of(banner, FileType.COMMITTEE_BANNER)
            committee.icon = pictures.of(icon, FileType.COMMITTEE_ICON)
            applyGames(committee, gameCodes)
            return repository.saveAndFlush(committee)
        }

        /** A committee that stopped running, or runs again. */
        @Transactional
        fun archive(
            id: Long,
            archived: Boolean,
        ): Committee {
            val committee = findById(id)
            committee.archived = archived
            return repository.saveAndFlush(committee)
        }

        /**
         * Makes exactly [committeeIds] organise events for the game [code], from the game's side.
         * Only the committees that change are written, and an archived game keeps the committees
         * it has but gains none.
         */
        @Transactional
        fun organisersOf(
            code: String,
            committeeIds: Set<Long>,
        ): List<Committee> {
            val game = games.requireCode(code)
            val all = repository.findAll()
            all.forEach { committee ->
                val wanted = committee.id in committeeIds
                val named = game in committee.gameCodes
                when {
                    wanted && !named -> committee.gameCodes += games.requireNameable(listOf(game)).single()
                    !wanted && named -> committee.gameCodes -= game
                    else -> return@forEach
                }
                repository.saveAndFlush(committee)
            }
            return all.filter { game in it.gameCodes }
        }

        private fun applyPage(
            committee: Committee,
            page: CommitteePage,
        ) {
            committee.slug = addressFor(page.address ?: committee.name, committee)
            committee.banner = pictures.of(page.banner, FileType.COMMITTEE_BANNER)
            committee.icon = pictures.of(page.icon, FileType.COMMITTEE_ICON)
            applyGames(committee, page.gameCodes)
        }

        // An archived game the committee already names stays named; one cannot be newly picked.
        private fun applyGames(
            committee: Committee,
            gameCodes: List<String>?,
        ) {
            val codes = gameCodes ?: return
            val named = games.requireNameable(codes, kept = committee.gameCodes.toSet())
            committee.gameCodes.clear()
            committee.gameCodes.addAll(named)
        }

        private fun addressFor(
            asked: String,
            committee: Committee,
        ): String {
            val address = addressOf(asked)
            if (address.isBlank()) throw CommitteeAddressBlank()
            val holder = repository.findBySlug(address)
            if (holder != null && holder.id != committee.id) throw CommitteeAddressTaken(holder.name, address)
            return address
        }

        /** How many committees run, for a reader outside this module. */
        @Transactional(readOnly = true)
        fun count(): Long = repository.count()

        fun findAllByUserId(id: Long): MutableList<Committee> = repository.findAllByUserId(id) as MutableList<Committee>

        private fun reconcileMembers(
            committee: Committee,
            members: List<CommitteeMemberData>,
        ) {
            val existingByUserId = committee.members.associateBy { it.userId }
            val mappedMembers =
                members.map { memberData ->
                    val member =
                        existingByUserId[memberData.userId] ?: CommitteeMember(
                            committee = committee,
                            user = userService.findById(memberData.userId),
                        )
                    member.role = memberData.role
                    member
                }
            committee.replaceMembers(mappedMembers)
        }

        private fun changedUserIds(
            previousMembers: Map<Long, String?>,
            currentMembers: Map<Long, String?>,
        ): Set<Long> {
            val removed = previousMembers.keys - currentMembers.keys
            val added = currentMembers.keys - previousMembers.keys
            val roleChanged =
                previousMembers.keys
                    .intersect(currentMembers.keys)
                    .filter { userId -> previousMembers[userId] != currentMembers[userId] }
                    .toSet()
            return removed + added + roleChanged
        }

        private fun publishMembershipChanges(
            committeeId: Long,
            userIds: Set<Long>,
        ) {
            userIds.forEach { userId ->
                trackedEvents.publish { actor ->
                    CommitteeMembershipChanged(
                        userId = userId,
                        committeeId = committeeId,
                        actor = actor,
                    )
                }
            }
        }
    }
