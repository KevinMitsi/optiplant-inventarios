package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pruebas del modelo de dominio {@link Branch}.
 *
 * <p>No hay contexto de Spring ni base de datos, y ese es justamente el argumento a favor
 * de separar el dominio de la entidad JPA: las reglas se comprueban en milisegundos y sin
 * infraestructura. Si {@code Branch} llevara anotaciones de JPA, esta clase necesitaría un
 * contenedor para ejecutarse.
 */
@DisplayName("Branch (modelo de dominio)")
class BranchTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();

    private static Branch newBranch() {
        return Branch.create(ORGANIZATION_ID, "BOG-01", "Sucursal Chapinero",
                "Calle 63 #11-24", "Bogotá", "CO", "+57 601 5551234");
    }

    @Nested
    @DisplayName("Creación")
    class Creation {

        @Test
        @DisplayName("una sucursal nueva nace activa y con identificador propio")
        void createsActiveBranchWithGeneratedId() {
            Branch branch = newBranch();

            assertThat(branch.getId()).isNotNull();
            assertThat(branch.getOrganizationId()).isEqualTo(ORGANIZATION_ID);
            assertThat(branch.isActive())
                    .as("registrar una sucursal implica la intención de operar con ella")
                    .isTrue();
            assertThat(branch.canOperate()).isTrue();
            assertThat(branch.getCreatedAt()).isNotNull();
            assertThat(branch.getUpdatedAt()).isEqualTo(branch.getCreatedAt());
        }

        @Test
        @DisplayName("el código se normaliza a mayúsculas y se recortan los espacios")
        void normalizesCode() {
            Branch branch = Branch.create(ORGANIZATION_ID, "  bog-01  ", "Sucursal",
                    null, null, null, null);

            // Sin esta normalización, 'bog-01' y 'BOG-01' pasarían la comprobación de
            // duplicados como si fueran códigos distintos.
            assertThat(branch.getCode()).isEqualTo("BOG-01");
        }

        @Test
        @DisplayName("el código de país se normaliza a mayúsculas")
        void normalizesCountryCode() {
            Branch branch = Branch.create(ORGANIZATION_ID, "BOG-01", "Sucursal",
                    null, null, "co", null);

            assertThat(branch.getCountryCode()).isEqualTo("CO");
        }

        @Test
        @DisplayName("los campos opcionales en blanco se guardan como nulos, no como cadena vacía")
        void blankOptionalFieldsBecomeNull() {
            Branch branch = Branch.create(ORGANIZATION_ID, "BOG-01", "Sucursal",
                    "   ", "", null, "  ");

            assertThat(branch.getAddressLine()).isNull();
            assertThat(branch.getCity()).isNull();
            assertThat(branch.getCountryCode()).isNull();
            assertThat(branch.getPhone()).isNull();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t"})
        @DisplayName("rechaza un código ausente o en blanco")
        void rejectsBlankCode(String code) {
            assertThatThrownBy(() -> Branch.create(ORGANIZATION_ID, code, "Sucursal",
                    null, null, null, null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("código");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("rechaza un nombre ausente o en blanco")
        void rejectsBlankName(String name) {
            assertThatThrownBy(() -> Branch.create(ORGANIZATION_ID, "BOG-01", name,
                    null, null, null, null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("nombre");
        }

        @Test
        @DisplayName("rechaza una sucursal sin organización")
        void rejectsMissingOrganization() {
            assertThatThrownBy(() -> Branch.create(null, "BOG-01", "Sucursal",
                    null, null, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("organización");
        }

        @ParameterizedTest
        @ValueSource(strings = {"C", "COL", "COLOMBIA"})
        @DisplayName("rechaza un código de país que no tenga exactamente dos letras")
        void rejectsInvalidCountryCode(String countryCode) {
            assertThatThrownBy(() -> Branch.create(ORGANIZATION_ID, "BOG-01", "Sucursal",
                    null, null, countryCode, null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("2 caracteres");
        }

        @Test
        @DisplayName("rechaza un código que supere los 30 caracteres")
        void rejectsTooLongCode() {
            String tooLong = "X".repeat(31);

            assertThatThrownBy(() -> Branch.create(ORGANIZATION_ID, tooLong, "Sucursal",
                    null, null, null, null))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("30");
        }
    }

    @Nested
    @DisplayName("Modificación")
    class Modification {

        @Test
        @DisplayName("actualizar los datos no altera la identidad de la sucursal")
        void updateKeepsIdentity() {
            Branch branch = newBranch();
            UUID originalId = branch.getId();
            String originalCode = branch.getCode();
            Instant originalCreatedAt = branch.getCreatedAt();

            branch.updateDetails("Sucursal Chapinero Norte", "Calle 72 #10-34",
                    "Bogotá", "CO", "+57 601 5559876");

            assertThat(branch.getName()).isEqualTo("Sucursal Chapinero Norte");
            assertThat(branch.getAddressLine()).isEqualTo("Calle 72 #10-34");

            // El código y la fecha de creación son inmutables: aparecen en documentos ya
            // emitidos y cambiarlos rompería la trazabilidad de lo registrado.
            assertThat(branch.getId()).isEqualTo(originalId);
            assertThat(branch.getCode()).isEqualTo(originalCode);
            assertThat(branch.getCreatedAt()).isEqualTo(originalCreatedAt);
        }

        @Test
        @DisplayName("actualizar los datos avanza la marca de última modificación")
        void updateAdvancesUpdatedAt() {
            Branch branch = newBranch();
            Instant before = branch.getUpdatedAt();

            branch.updateDetails("Otro nombre", null, null, null, null);

            assertThat(branch.getUpdatedAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("rechaza dejar la sucursal sin nombre")
        void rejectsBlankNameOnUpdate() {
            Branch branch = newBranch();

            assertThatThrownBy(() -> branch.updateDetails("  ", null, null, null, null))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    @DisplayName("Estado de alta")
    class Status {

        @Test
        @DisplayName("una sucursal dada de baja deja de poder operar")
        void deactivatedBranchCannotOperate() {
            Branch branch = newBranch();

            branch.deactivate();

            assertThat(branch.isActive()).isFalse();
            assertThat(branch.canOperate())
                    .as("una sucursal inactiva puede consultarse, pero no mover mercancía")
                    .isFalse();
        }

        @Test
        @DisplayName("dar de baja dos veces no es un error")
        void deactivationIsIdempotent() {
            Branch branch = newBranch();
            branch.deactivate();
            Instant afterFirst = branch.getUpdatedAt();

            assertThatCode(branch::deactivate).doesNotThrowAnyException();

            assertThat(branch.isActive()).isFalse();
            assertThat(branch.getUpdatedAt())
                    .as("una llamada sin efecto no debe simular una modificación")
                    .isEqualTo(afterFirst);
        }

        @Test
        @DisplayName("reactivar devuelve la sucursal a la operación")
        void activationRestoresOperation() {
            Branch branch = newBranch();
            branch.deactivate();

            branch.activate();

            assertThat(branch.isActive()).isTrue();
            assertThat(branch.canOperate()).isTrue();
        }

        @Test
        @DisplayName("reactivar una sucursal ya activa no es un error")
        void activationIsIdempotent() {
            Branch branch = newBranch();
            Instant before = branch.getUpdatedAt();

            assertThatCode(branch::activate).doesNotThrowAnyException();

            assertThat(branch.isActive()).isTrue();
            assertThat(branch.getUpdatedAt()).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("Identidad")
    class Identity {

        @Test
        @DisplayName("dos sucursales con el mismo identificador son la misma aunque difieran sus datos")
        void equalityIsByIdentityNotByAttributes() {
            Branch original = newBranch();
            Branch reloaded = Branch.reconstitute(
                    original.getId(), original.getOrganizationId(), original.getCode(),
                    "Un nombre completamente distinto", null, null, null, null,
                    false, original.getCreatedAt(), Instant.now());

            // Semántica de entidad: la identidad la da el identificador, no el contenido.
            assertThat(reloaded).isEqualTo(original);
            assertThat(reloaded).hasSameHashCodeAs(original);
        }

        @Test
        @DisplayName("dos sucursales distintas no son iguales aunque compartan todos los demás datos")
        void differentIdsAreNotEqual() {
            Branch first = newBranch();
            Branch second = newBranch();

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("Reconstitución desde persistencia")
    class Reconstitution {

        @Test
        @DisplayName("respeta el identificador y las marcas de tiempo almacenados")
        void preservesStoredValues() {
            UUID id = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-01-15T09:30:00Z");
            Instant updatedAt = Instant.parse("2026-08-27T14:05:22Z");

            Branch branch = Branch.reconstitute(id, ORGANIZATION_ID, "BOG-01", "Sucursal",
                    null, null, null, null, false, createdAt, updatedAt);

            assertThat(branch.getId()).isEqualTo(id);
            assertThat(branch.getCreatedAt()).isEqualTo(createdAt);
            assertThat(branch.getUpdatedAt()).isEqualTo(updatedAt);
            assertThat(branch.isActive()).isFalse();
        }

        @Test
        @DisplayName("sigue validando los invariantes, de modo que un dato corrupto salta al leerlo")
        void stillValidatesInvariants() {
            assertThatThrownBy(() -> Branch.reconstitute(
                    UUID.randomUUID(), ORGANIZATION_ID, "BOG-01", "  ",
                    null, null, null, null, true, Instant.now(), Instant.now()))
                    .isInstanceOf(DomainValidationException.class);
        }
    }
}
