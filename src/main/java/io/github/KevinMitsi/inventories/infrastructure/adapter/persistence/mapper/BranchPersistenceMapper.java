package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.BranchJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Traduce entre el modelo de dominio {@link Branch} y la entidad persistente.
 *
 * <p>Es la costura del adaptador de persistencia. Su existencia es lo que permite que
 * {@code Branch} no lleve anotaciones de JPA y siga siendo comprobable sin base de datos.
 *
 * <p>Las dos direcciones no son simétricas, y con razón:
 * <ul>
 *   <li><b>Dominio a entidad</b> lo genera MapStruct entero. La entidad tiene constructor
 *       de Lombok y nombres de propiedad coincidentes, así que la correspondencia es
 *       directa y el código se escribe solo en tiempo de compilación.</li>
 *   <li><b>Entidad a dominio</b> se escribe a mano, porque {@code Branch} no tiene
 *       constructor público ni asignadores: se construye por {@code reconstitute}, que
 *       vuelve a validar los invariantes. Es justo lo que se quiere, porque hace que un
 *       dato corrupto en la base salte al leerlo y no varias operaciones más tarde. Un
 *       mapeador que rellenara campos por reflexión se saltaría esa validación.</li>
 * </ul>
 *
 * <p>MapStruct genera la implementación en tiempo de compilación: sin reflexión en
 * ejecución y con los errores de correspondencia detectados al compilar, no en producción.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BranchPersistenceMapper {

    /**
     * Dominio a entidad.
     *
     * <p>{@code unmappedTargetPolicy = ERROR} obliga a que toda propiedad de la entidad
     * quede cubierta. Si mañana se añade una columna y nadie actualiza el mapeo, la
     * compilación falla en lugar de persistir silenciosamente un nulo.
     */
    BranchJpaEntity toEntity(Branch branch);

    /** Entidad a dominio, pasando por la factoría que revalida los invariantes. */
    default Branch toDomain(BranchJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Branch.reconstitute(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getCode(),
                entity.getName(),
                entity.getAddressLine(),
                entity.getCity(),
                entity.getCountryCode(),
                entity.getPhone(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
