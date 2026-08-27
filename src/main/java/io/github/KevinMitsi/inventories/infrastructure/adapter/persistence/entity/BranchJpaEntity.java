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

/**
 * Representación persistente de una sucursal.
 *
 * <p>Es deliberadamente anémica: solo estado y anotaciones de mapeo. Toda la lógica vive
 * en {@link io.github.KevinMitsi.inventories.domain.model.Branch}, y un mapeador traduce
 * entre ambas. Sin esa separación, JPA acabaría dictando el diseño del dominio —campos no
 * finales, constructor vacío, carga perezosa filtrándose a las reglas de negocio— y las
 * pruebas de negocio necesitarían una base de datos.
 *
 * <p><b>Referencia a la organización por identificador, no por {@code @ManyToOne}.</b>
 * Sucursal y organización son agregados distintos, y la regla que se sigue en todo el
 * proyecto es que entre agregados se referencia por identificador. Tres motivos concretos:
 * <ul>
 *   <li>El N+1 entre agregados deja de ser posible. No hay proxy que se pueda desreferenciar
 *       dentro de un bucle y disparar una consulta por elemento.</li>
 *   <li>El límite del agregado queda explícito. Cargar una sucursal no arrastra media base
 *       de datos por navegación accidental.</li>
 *   <li>La integridad referencial no se pierde: la clave foránea sigue declarada en la
 *       migración y PostgreSQL la aplica igual.</li>
 * </ul>
 * Cuando una consulta necesita datos de ambos, el adaptador hace la unión de forma
 * explícita mediante una proyección, que es la operación que realmente se quería.
 *
 * <p>{@code @ManyToOne} y {@code @EntityGraph} sí se emplean <em>dentro</em> de un agregado
 * —una venta y sus líneas, una transferencia y sus ítems—, donde la carga conjunta es lo
 * correcto y el grafo evita justamente el N+1.
 */
@Entity
@Table(name = "branch")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BranchJpaEntity {

    /**
     * Identificador generado por el dominio, no por la base.
     *
     * <p>No lleva {@code @GeneratedValue}: {@code Branch.create()} ya asigna un UUID. Eso
     * permite construir y relacionar el agregado completo antes de que exista ninguna fila,
     * en lugar de depender de un valor que solo aparece tras el INSERT.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "code", nullable = false, updatable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address_line", length = 250)
    private String addressLine;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country_code", columnDefinition = "char(2)", length = 2)
    private String countryCode;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Igualdad por identificador.
     *
     * <p>Lombok generaría {@code equals} sobre todos los campos, lo que rompe el contrato en
     * cuanto una instancia se modifica estando dentro de una colección: cambiaría su
     * {@code hashCode} y dejaría de encontrarse. Por eso se escribe a mano y se compara solo
     * el identificador, que es lo único que no cambia en toda la vida de la entidad.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof BranchJpaEntity entity && id != null && id.equals(entity.id);
    }

    /**
     * Constante y no derivado del identificador.
     *
     * <p>Mantiene el mismo valor antes y después de persistir, condición necesaria para que
     * la entidad siga localizable dentro de un {@code HashSet} tras el guardado.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
