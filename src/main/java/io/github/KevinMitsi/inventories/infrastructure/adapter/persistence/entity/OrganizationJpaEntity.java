package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Representación persistente de una organización. Solo estado y mapeo; la lógica vive en el dominio. */
@Entity
@Table(name = "organization")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OrganizationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof OrganizationJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
