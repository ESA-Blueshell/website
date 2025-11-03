package net.blueshell.api.model.board;

import jakarta.persistence.*;
import lombok.*;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.File;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.Set;

@Entity
@Table(
        name = "boards",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_boards_name_start_date_deleted_at",
                        columnNames = {"name", "start_date", "deleted_at"}
                ),
                @UniqueConstraint(
                        name = "uk_boards_picture_deleted_at",
                        columnNames = {"picture_id", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_boards_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_boards_name", columnList = "name"),
                @Index(name = "idx_boards_start_date", columnList = "start_date"),
                @Index(name = "idx_boards_end_date", columnList = "end_date")
        }
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE boards SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
public class Board extends BaseModel {
    @Column(name = "name", nullable = false)
    @ToString.Include
    private String name;

    @JoinColumn(name = "picture_id")
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private File picture;

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<BoardMember> members;

    @Column(name = "candidate", nullable = false)
    @ToString.Include
    private String candidate;

    @Column(name = "start_date", nullable = false)
    @ToString.Include
    private LocalDate startDate;

    @Column(name = "end_date")
    @ToString.Include
    private LocalDate endDate;

    @OneToMany(mappedBy = "board")
    private Set<BoardDocument> documents;
}
