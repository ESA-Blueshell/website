package net.blueshell.blogservice;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.db.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(name = "blogs")
@SQLDelete(sql = "UPDATE blogs SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Data
public class Blog implements BaseModel<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    private String text;

    private String html;

    @Column(name = "created_at")
    private Timestamp createdAt = new Timestamp(System.currentTimeMillis());

    @Column(name = "deleted_at")
    private Timestamp deletedAt;
}
