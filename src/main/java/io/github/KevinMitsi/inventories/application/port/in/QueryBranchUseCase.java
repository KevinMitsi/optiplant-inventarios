package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.BranchSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;

/**
 * Consulta de sucursales (HU-05, RF-06).
 *
 * <p>Sostiene una capacidad central del sistema: cualquier usuario autorizado puede ver
 * qué sucursales existen para saber dónde hay inventario antes de pedir una transferencia
 * (HU-06). Por eso la lectura no queda restringida a la sucursal propia, a diferencia de
 * las operaciones de escritura.
 */
public interface QueryBranchUseCase {

    /**
     * Recupera una sucursal por identificador.
     *
     * @throws io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException
     *         si no existe
     */
    Branch getBranchById(UUID branchId);

    /** Recupera una sucursal por su código de negocio dentro de la organización. */
    Branch getBranchByCode(UUID organizationId, String code);

    /** Lista sucursales filtradas y paginadas. */
    PageResult<Branch> searchBranches(BranchSearchCriteria criteria, PageQuery pageQuery);
}
