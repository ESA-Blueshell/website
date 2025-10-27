// Address.java
package net.blueshell.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
        name = "addresses",
        indexes = {
                @Index(name = "idx_addresses_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_addresses_city", columnList = "city"),
                @Index(name = "idx_addresses_zip_code", columnList = "zip_code")
        }
)
@SQLDelete(sql = "UPDATE addresses SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
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
}
