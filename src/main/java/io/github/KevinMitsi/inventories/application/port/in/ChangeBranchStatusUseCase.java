package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.domain.model.Branch;

import java.util.UUID;

/**
 * Alta y baja lógica de una sucursal.
 *
 * <p>Separado de la edición de datos porque no es lo mismo: cambiar el nombre es corregir
 * una ficha, mientras que dar de baja retira a la sucursal de toda operación futura sobre
 * inventario. Tienen consecuencias distintas y, previsiblemente, permisos distintos.
 *
 * <p>Nunca hay borrado físico. La sucursal aparece en ventas, compras y movimientos
 * históricos; eliminarla dejaría esos registros sin poder explicarse (ENTITIES.md §30).
 */
public interface ChangeBranchStatusUseCase {

    /**
     * Retira la sucursal de la operación. Idempotente.
     *
     * @throws io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException
     *         si la sucursal no existe
     */
    Branch deactivateBranch(UUID branchId);

    /** Devuelve la sucursal a la operación. Idempotente. */
    Branch activateBranch(UUID branchId);
}
