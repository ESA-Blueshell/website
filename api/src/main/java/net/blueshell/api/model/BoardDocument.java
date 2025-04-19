package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "board_documents")
@SQLRestriction("deleted_at IS NULL")
@SQLDelete(sql = "UPDATE board_documents SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class BoardDocument implements BaseModel<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "board_id")
    @ManyToOne
    private Board board;

    @Column(name = "name")
    private String name;

    @JoinColumn(name = "file_id")
    @OneToOne
    private File file;
}
