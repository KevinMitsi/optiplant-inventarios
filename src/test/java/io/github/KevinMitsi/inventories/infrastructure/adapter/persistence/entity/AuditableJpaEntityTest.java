package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de los callbacks de auditoría.
 *
 * <p>Se invocan directamente en lugar de a través de un contexto de persistencia: lo que
 * interesa comprobar es la decisión de cuándo se rellena cada marca, no que Hibernate sepa
 * llamar a un método anotado.
 */
@DisplayName("AuditableJpaEntity (callbacks de auditoría)")
class AuditableJpaEntityTest {

    private BranchJpaEntity newEntity() {
        return BranchJpaEntity.builder()
                .id(UUID.randomUUID())
                .organizationId(UUID.randomUUID())
                .code("BOG-01")
                .name("Sucursal Chapinero")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("al persistir rellena ambas marcas cuando vienen vacías")
    void prePersistFillsMissingTimestamps() {
        // Arrange
        BranchJpaEntity entity = newEntity();
        assertThat(entity.getCreatedAt()).isNull();

        // Act
        entity.onCreate();

        // Assert
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("al persistir respeta las marcas que ya trae el modelo de dominio")
    void prePersistPreservesExistingTimestamps() {
        // Arrange
        Instant createdAt = Instant.parse("2026-01-15T09:30:00Z");
        Instant updatedAt = Instant.parse("2026-03-20T11:00:00Z");
        BranchJpaEntity entity = newEntity();
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);

        // Act
        entity.onCreate();

        // Assert: reconstituir desde la base no debe reescribir la fecha original
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("al actualizar avanza la marca de modificación y conserva la de creación")
    void preUpdateAdvancesOnlyUpdatedAt() {
        // Arrange
        Instant createdAt = Instant.parse("2026-01-15T09:30:00Z");
        Instant updatedAt = Instant.parse("2026-03-20T11:00:00Z");
        BranchJpaEntity entity = newEntity();
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);

        // Act
        entity.onUpdate();

        // Assert
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    @DisplayName("la igualdad de las entidades es por identificador, no por atributos")
    void equalityIsByIdentifier() {
        // Arrange
        UUID sharedId = UUID.randomUUID();
        BranchJpaEntity first = newEntity();
        first.setId(sharedId);
        BranchJpaEntity second = newEntity();
        second.setId(sharedId);
        second.setName("Otro nombre completamente distinto");

        // Act & Assert
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("el hashCode no depende del identificador, para sobrevivir al guardado")
    void hashCodeIsStableAcrossPersistence() {
        // Arrange
        BranchJpaEntity entity = newEntity();
        entity.setId(null);
        int beforeAssigningId = entity.hashCode();

        // Act
        entity.setId(UUID.randomUUID());

        // Assert: si cambiara, la entidad dejaría de encontrarse dentro de un HashSet
        assertThat(entity.hashCode()).isEqualTo(beforeAssigningId);
    }
}
