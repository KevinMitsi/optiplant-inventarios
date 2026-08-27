package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.UpdateBranchCommand;
import io.github.KevinMitsi.inventories.domain.model.Branch;

/** Modificación de los datos descriptivos de una sucursal (RF-05). */
public interface UpdateBranchUseCase {

    /**
     * Actualiza nombre, dirección, ciudad, país y teléfono.
     *
     * <p>Ni el código ni la organización son modificables: identifican a la sucursal y
     * aparecen en documentos ya emitidos.
     *
     * @throws io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException
     *         si la sucursal no existe
     */
    Branch updateBranch(UpdateBranchCommand command);
}
