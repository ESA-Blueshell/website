package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;

@Entity
@Table(name = "event_pictures")
@Data
@SQLDelete(sql = "UPDATE event_pictures SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class EventPicture implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "picture_id")
    private File picture;

    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;
}
