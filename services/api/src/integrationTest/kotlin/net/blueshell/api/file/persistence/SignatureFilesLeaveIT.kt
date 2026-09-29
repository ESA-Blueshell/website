package net.blueshell.api.file.persistence

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.yaml.snakeyaml.Yaml

/**
 * The signature changeset (#1942) against MariaDB: signature rows and their renditions leave,
 * the bytes only they point at are queued, and every other file stays.
 */
@SpringBootTest
@Transactional
class SignatureFilesLeaveIT : UserTestSupport() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    // The test database ran this changeset while files was empty, so it is run again on rows.
    @Suppress("UNCHECKED_CAST")
    private val statements: List<String> =
        javaClass.getResourceAsStream("/db/changelog/changes/2026-09-29-signature-files-leave.yaml")!!.use { yaml ->
            val changelog = Yaml().load<Map<String, List<Map<String, Map<String, Any>>>>>(yaml)
            val changeSet =
                changelog
                    .getValue("databaseChangeLog")
                    .map { it.getValue("changeSet") }
                    .single { it["id"] == "signature-files-leave" }
            (changeSet.getValue("changes") as List<Map<String, Map<String, String>>>).map { it.getValue("sql").getValue("sql") }
        }

    private fun file(
        uploader: Long,
        type: String,
        path: String,
        source: Long? = null,
        deletedAt: String = "9999-12-31 23:59:59",
    ): Long {
        jdbc.update(
            "INSERT INTO files (name, uploader_id, media_type, type, path, source_file_id, deleted_at) " +
                "VALUES (?, ?, 'image/png', ?, ?, ?, ?)",
            path.substringAfterLast('/'),
            uploader,
            type,
            path,
            source,
            deletedAt,
        )
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    @Test
    fun `signatures and their renditions leave, only their own bytes are queued, and other files stay`() {
        val uploader = requireNotNull(createUserWithRole(Role.MEMBER).id)
        val signature = file(uploader, "SIGNATURE", "signatures/one.png")
        file(uploader, "SIGNATURE", "signatures/one-320.webp", source = signature)
        file(uploader, "SIGNATURE", "event-banners/shared.webp", deletedAt = "2024-01-01 00:00:00")
        val banner = file(uploader, "EVENT_BANNER", "event-banners/shared.webp")

        statements.forEach(jdbc::execute)

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE type = 'SIGNATURE'", Int::class.java)).isZero()
        assertThat(jdbc.queryForList("SELECT path FROM blobs_to_delete", String::class.java))
            .containsExactlyInAnyOrder("signatures/one.png", "signatures/one-320.webp")
        assertThat(jdbc.queryForObject("SELECT path FROM files WHERE id = ?", String::class.java, banner))
            .isEqualTo("event-banners/shared.webp")
    }
}
