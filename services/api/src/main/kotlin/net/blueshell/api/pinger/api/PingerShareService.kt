package net.blueshell.api.pinger.api

import net.blueshell.api.pinger.persistence.PingerLiveStore
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The slice [from, to) of the unit interval a device paints, out of [devices] sharing it. */
data class PingerShare(
    val from: Double,
    val to: Double,
    val devices: Int,
)

/**
 * Splits the paint between the pingers running now, so devices stop all painting the same pixels.
 *
 * Every online device, plus the asking one, joins one of two groups, and each group tiles [0, 1)
 * with slices proportional to its members' reported rates. Every pixel so has two painters: a
 * reported rate counts packets leaving the device, and a router that drops them would otherwise
 * leave that device's slice bare. Members are placed in a fixed order by identity then device id so
 * the slices hold still between polls. A client keeps
 * pixel i (0-based, placements concatenated in descriptor order) iff frac((i + 1) * 0.6180339887498949)
 * falls in its slice. The clients in services/pinger and services/pinger-app apply that rule; change
 * one, change the others.
 */
@Service
class PingerShareService(
    private val live: PingerLiveStore,
    private val clock: Clock,
) {
    private class Participant(
        val identity: String,
        val deviceId: String,
        val weight: Long,
    )

    private class Snapshot(
        val at: Instant,
        val online: List<Participant>,
    )

    @Volatile
    private var snapshot: Snapshot? = null

    fun share(
        identity: String,
        deviceId: String,
    ): PingerShare {
        val online = online()
        val participants =
            if (online.any { it.identity == identity && it.deviceId == deviceId }) {
                online
            } else {
                (online + Participant(identity, deviceId, FLOOR_PPS)).sortedWith(ORDER)
            }
        val group = groups(participants).first { members -> members.any { it.identity == identity && it.deviceId == deviceId } }
        val total = group.sumOf { it.weight }
        val index = group.indexOfFirst { it.identity == identity && it.deviceId == deviceId }
        val before = group.subList(0, index).sumOf { it.weight }
        // The last slice ends at exactly 1.0, so rounding can never leave the top pixels unclaimed.
        val to = if (index == group.lastIndex) 1.0 else (before + group[index].weight).toDouble() / total
        return PingerShare(from = before.toDouble() / total, to = to, devices = participants.size)
    }

    // Each participant, in order, joins the lighter group, so both carry about half the rate.
    private fun groups(participants: List<Participant>): List<List<Participant>> {
        val first = mutableListOf<Participant>()
        val second = mutableListOf<Participant>()
        participants.forEach { p -> if (first.sumOf { it.weight } <= second.sumOf { it.weight }) first += p else second += p }
        return listOf(first, second)
    }

    // Every stream subscriber and poller asks within the same second, so one keyspace walk serves them all.
    private fun online(): List<Participant> {
        val now = clock.instant()
        snapshot?.takeIf { now < it.at.plus(CACHE_FOR) }?.let { return it.online }
        val online =
            live
                .devices()
                .filter { it.live.online }
                .map { Participant(it.identity, it.deviceId, maxOf(it.live.pps.toLong(), FLOOR_PPS)) }
                .sortedWith(ORDER)
        snapshot = Snapshot(now, online)
        return online
    }

    private companion object {
        // A just-started or idle device reports a low or zero rate; without a floor it would get no
        // slice, and its pixels would go unpainted until its first fast report.
        const val FLOOR_PPS = 1_000L
        val CACHE_FOR: Duration = Duration.ofSeconds(1)
        val ORDER: Comparator<Participant> = compareBy<Participant>({ it.identity }, { it.deviceId })
    }
}
