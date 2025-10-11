package net.blueshell.api.model.board;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.File;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.Date;
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
                @Index(name = "idx_boards_name", columnList = "name"),
                @Index(name = "idx_boards_start_date", columnList = "start_date"),
                @Index(name = "idx_boards_end_date", columnList = "end_date")
        }
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE boards SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class Board implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @JoinColumn(name = "picture_id")
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private File picture;

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<BoardMember> members;

    @Column(name = "candidate")
    private String candidate;

    @Column(name = "start_date")
    private Date startDate;

    @Column(name = "end_date")
    private Date endDate;

    @OneToMany(mappedBy = "board")
    private Set<BoardDocument> documents;

    @Column(name = "deleted_at", nullable = false, insertable=false, updatable = false)
    private Timestamp deletedAt = Timestamp.valueOf("9999-12-31 23:59:59");
}
