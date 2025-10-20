package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.*;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;

@Entity
@Table(
        name = "addresses",
        indexes = {
                @Index(name = "idx_addresses_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_addresses_city", columnList = "city"),
                @Index(name = "idx_addresses_zip_code", columnList = "zip_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Address extends BaseModel {
    @Column
    private String country;

    @Column
    private String city;

    @Column
    private String street;

    @Column(name = "house_number")
    private String houseNumber;

    @Column(name = "zip_code")
    private String zipCode;

    @OneToOne(mappedBy = "address")
    @ToString.Exclude
    private User user;
}