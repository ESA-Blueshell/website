package net.blueshell.api.model

import jakarta.persistence.*
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import lombok.ToString
import net.blueshell.api.base.BaseModel
import net.blueshell.api.common.enums.PlatformType
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "telemetries",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_telemetries_platform_url_deleted_at",
        columnNames = ["platform", "url", "deleted_at"]
    )],
    indexes = [Index(
        name = "idx_telemetries_deleted_at",
        columnList = "deleted_at"
    ), Index(name = "idx_telemetries_platform", columnList = "platform"), Index(
        name = "idx_telemetries_url",
        columnList = "url"
    ), Index(name = "idx_telemetries_created_at", columnList = "created_at")]
)
@SQLDelete(sql = "UPDATE telemetries SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
class Telemetry(
    @field:Column(nullable = false) @field:Enumerated(EnumType.STRING) private var platform: PlatformType?,
    @field:Column(
        nullable = false
    ) private var url: String?
) : BaseModel() {
    @OneToMany(mappedBy = "telemetry", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val redirects: MutableSet<Redirect?>? = null
}
