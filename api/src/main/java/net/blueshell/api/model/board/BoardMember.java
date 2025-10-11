package net.blueshell.api.model.board;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;

@Entity
@Table(
        name = "board_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_board_members_board_user_deleted_at",
                        columnNames = {"board_id", "user_id", "deleted_at"}
                ),
                @UniqueConstraint(
                        name = "uk_board_members_picture_deleted_at",
                        columnNames = {"picture_id", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_board_members_board_id", columnList = "board_id"),
                @Index(name = "idx_board_members_user_id", columnList = "user_id")
        }
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE board_members SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class BoardMember implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "board_id", nullable = false)
    @ManyToOne
    private Board board;

    @JoinColumn(name = "user_id", nullable = false)
    @ManyToOne
    private User user;

    @JoinColumn(name = "picture_id")
    @OneToOne
    private File picture;

    @Column(name = "deleted_at", nullable = false, insertable=false, updatable = false)
    @ColumnDefault("9999-12-31 23:59:59")
    private Timestamp deletedAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Generated
    private Timestamp createdAt;
}
