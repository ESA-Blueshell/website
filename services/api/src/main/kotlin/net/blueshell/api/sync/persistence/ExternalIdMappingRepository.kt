package net.blueshell.api.sync.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ExternalIdMappingRepository : BaseRepository<ExternalIdMapping, Long> {
    fun findByAggregateTypeAndAggregateIdAndSystem(
        aggregateType: String,
        aggregateId: Long,
        system: String,
    ): ExternalIdMapping?

    fun findByAggregateTypeAndSystemAndAggregateIdIn(
        aggregateType: String,
        system: String,
        aggregateIds: Collection<Long>,
    ): List<ExternalIdMapping>

    fun findByAggregateTypeAndSystemAndExternalIdIn(
        aggregateType: String,
        system: String,
        externalIds: Collection<String>,
    ): List<ExternalIdMapping>

    fun findFirstByAggregateTypeAndSystemAndExternalId(
        aggregateType: String,
        system: String,
        externalId: String,
    ): ExternalIdMapping?

    /* One row per aggregate and system is the unique key, so only one caller's insert lands. */
    @Modifying
    @Query(
        value =
            "INSERT IGNORE INTO external_id_mapping (aggregate_type, aggregate_id, `system`, version) " +
                "VALUES (:aggregateType, :aggregateId, :system, 0)",
        nativeQuery = true,
    )
    fun insertClaim(
        @Param("aggregateType") aggregateType: String,
        @Param("aggregateId") aggregateId: Long,
        @Param("system") system: String,
    ): Int

    /* A claim still empty since [staleBefore] was left by a caller that never finished. */
    @Modifying
    @Query(
        value =
            "UPDATE external_id_mapping SET updated_at = CURRENT_TIMESTAMP, version = version + 1 " +
                "WHERE aggregate_type = :aggregateType AND aggregate_id = :aggregateId AND `system` = :system " +
                "AND external_id IS NULL AND updated_at < :staleBefore",
        nativeQuery = true,
    )
    fun takeOverStaleClaim(
        @Param("aggregateType") aggregateType: String,
        @Param("aggregateId") aggregateId: Long,
        @Param("system") system: String,
        @Param("staleBefore") staleBefore: Instant,
    ): Int

    @Modifying
    @Query(
        "delete from ExternalIdMapping m where m.aggregateType = :aggregateType " +
            "and m.aggregateId = :aggregateId and m.system = :system",
    )
    fun deleteMapping(
        @Param("aggregateType") aggregateType: String,
        @Param("aggregateId") aggregateId: Long,
        @Param("system") system: String,
    ): Int

    /*
     * A MariaDB named lock belongs to the connection, so both halves must run on one: inside the
     * caller's transaction. Answers 1 once held, 0 when [seconds] ran out.
     */
    @Query(value = "SELECT GET_LOCK(:name, :seconds)", nativeQuery = true)
    fun acquireNamedLock(
        @Param("name") name: String,
        @Param("seconds") seconds: Int,
    ): Int?

    @Query(value = "SELECT RELEASE_LOCK(:name)", nativeQuery = true)
    fun releaseNamedLock(
        @Param("name") name: String,
    ): Int?
}
