package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.Date;
import java.util.Set;

@Entity
@Table(name = "boards")
@SQLRestriction("deleted_at <= NOW()")
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
}
