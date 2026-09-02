package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.QueryActivityLogUseCase;
import io.github.KevinMitsi.inventories.application.port.in.RecordActivityLogUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.RecordActivityLogCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.ActivityLogSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.ActivityLog;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.usecase.ActivityLogUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Primary
@Service
@Transactional(rollbackFor = Exception.class)
public class ActivityLogService implements RecordActivityLogUseCase, QueryActivityLogUseCase {

    private final ActivityLogUseCase useCase;

    public ActivityLogService(ActivityLogUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * Escribe la entrada en una transacción propia.
     *
     * <p>{@code REQUIRES_NEW} es lo que separa la traza del destino de la operación que la
     * origina. Si compartieran transacción, un intento fallido —una venta rechazada por
     * stock insuficiente, un acceso denegado— se revertiría junto con su propio registro, y
     * la traza solo contendría lo que salió bien. Justo el caso que interesa auditar sería
     * el que desaparece.
     *
     * <p>El precio es una segunda conexión del pool mientras dura la escritura. Se asume a
     * conciencia: la alternativa es una traza que miente por omisión.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ActivityLog record(RecordActivityLogCommand command) {
        return useCase.record(command);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ActivityLog> searchActivityLogs(ActivityLogSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchActivityLogs(criteria, pageQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityLog getActivityLogById(UUID activityLogId) {
        return useCase.getActivityLogById(activityLogId);
    }
}
