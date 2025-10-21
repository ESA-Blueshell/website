package net.blueshell.api.model.board;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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
                @Index(name = "idx_board_members_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_board_members_board_id", columnList = "board_id"),
                @Index(name = "idx_board_members_user_id", columnList = "user_id")
        }
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE board_members SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@Getter
@Setter
@NoArgsConstructor
public class BoardMember extends BaseModel {
    @JoinColumn(name = "board_id", nullable = false)
    @ManyToOne
    private Board board;

    @JoinColumn(name = "user_id", nullable = false)
    @ManyToOne
    private User user;

    @JoinColumn(name = "picture_id")
    @OneToOne
    private File picture;
}
