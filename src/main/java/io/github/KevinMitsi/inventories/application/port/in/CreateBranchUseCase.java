package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateBranchCommand;
import io.github.KevinMitsi.inventories.domain.model.Branch;

/**
 * Alta de una sucursal en la red de la organización (HU-04, RF-05).
 *
 * <p>Interfaz de una sola operación, no un servicio con todos los métodos de sucursal.
 * Cada consumidor depende exactamente de lo que usa, así que el controlador que solo crea
 * no arrastra las firmas de consulta ni de baja, y un doble de prueba se construye con un
 * único método. Es el principio de segregación de interfaces aplicado a los casos de uso.
 */
public interface CreateBranchUseCase {

    /**
     * Registra una sucursal nueva.
     *
     * @throws io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException
     *         si la organización indicada no existe
     * @throws io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException
     *         si el código ya está en uso dentro de esa organización
     * @throws io.github.KevinMitsi.inventories.domain.exception.DomainValidationException
     *         si algún dato incumple los invariantes de la sucursal
     */
    Branch createBranch(CreateBranchCommand command);
}
