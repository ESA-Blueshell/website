package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;

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