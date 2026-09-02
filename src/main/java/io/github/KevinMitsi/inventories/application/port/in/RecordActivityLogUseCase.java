package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.command.RecordActivityLogCommand;
import io.github.KevinMitsi.inventories.domain.model.ActivityLog;

/**
 * Alta de una entrada en la traza de auditoría.
 *
 * <p>Lo invoca el manejador de registros de infraestructura, no un controlador: la traza no
 * se escribe desde fuera de la aplicación, se deduce de lo que los casos de uso ya registran.
 */
public interface RecordActivityLogUseCase {

    ActivityLog record(RecordActivityLogCommand command);
}
