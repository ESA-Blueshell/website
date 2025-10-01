package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;

@Entity
@Table(name = "blogs")
@SQLDelete(sql = "UPDATE blogs SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at >= NOW()")
@Data
public class Blog implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "html")
    private String html;

    @Column(name = "published_at")
    private Timestamp publishedAt;

    @Column(name = "created_at")
    private Timestamp createdAt;
}
