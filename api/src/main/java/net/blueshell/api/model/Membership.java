package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.common.enums.MemberType;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Date;
import java.util.Set;

@Entity
@Table(name = "memberships")
@SQLDelete(sql = "UPDATE memberships SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at >= NOW()")
@Data
@EntityListeners(JpaListener.class)
public class Membership implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @OneToOne(fetch = FetchType.LAZY)
    @ToString.Exclude
    private User user;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "city")
    private String city;

    @Column(name = "start_date")
    private Date startDate;

    @Column(name = "end_date")
    private Date endDate;

    @Column(name = "type")
    @Enumerated(EnumType.STRING)
    private MemberType memberType;

    @JoinColumn(name = "signature_id", updatable = false, insertable = false)
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private File signature;

    @OneToMany(mappedBy = "membership")
    private Set<Contribution> contributions;

    @Column(name = "incasso", nullable = false)
    private boolean incasso;
}
