package net.blueshell.api.model.board;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.File;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;

@Entity
@Table(name = "board_documents")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE board_documents SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Data
public class BoardDocument implements BaseModel {

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

    @Column(name = "deleted_at", nullable = false)
    private Timestamp deletedAt = Timestamp.valueOf("9999-12-31 23:59:59");
}
