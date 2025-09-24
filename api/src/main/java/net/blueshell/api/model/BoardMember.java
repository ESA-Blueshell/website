package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "board_members")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE board_members SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class BoardMember implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "board_id")
    @ManyToOne
    private Board board;

    @JoinColumn(name = "user_id")
    @ManyToOne
    private User user;

    @JoinColumn(name = "picture_id")
    @OneToOne
    private File picture;
}
