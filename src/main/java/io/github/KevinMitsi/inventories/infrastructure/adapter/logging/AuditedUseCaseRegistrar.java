package io.github.KevinMitsi.inventories.infrastructure.adapter.logging;

import io.github.KevinMitsi.inventories.domain.annotation.AuditedUseCase;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * Engancha {@link ActivityLogHandler} al logger de cada clase anotada con
 * {@link AuditedUseCase}.
 *
 * <p>Este es el único punto donde se paga el coste de la auditoría centralizada. Un caso de
 * uso nuevo entra en la traza con una anotación y nada más: no hay que acordarse de llamar a
 * ningún servicio de auditoría en cada método, que es justo la clase de repetición que
 * termina olvidándose en el método que más importaba.
 *
 * <p>Se hace en {@code afterSingletonsInstantiated} y no en un {@code ApplicationRunner}
 * porque los casos de uso ya registran cosas durante el arranque —el alta del primer
 * administrador, por ejemplo— y a esa altura el enganche ya debe estar puesto.
 *
 * <p>Los loggers de {@code java.util.logging} son estáticos y sobreviven al contexto de
 * Spring. Por eso se desenganchan al cerrarlo: sin ello, un segundo contexto —lo habitual en
 * una batería de pruebas— dejaría dos manejadores escribiendo, uno de ellos contra un
 * contexto ya cerrado.
 */
@Component
public class AuditedUseCaseRegistrar implements SmartInitializingSingleton, DisposableBean {

    private static final Logger log = Logger.getLogger(AuditedUseCaseRegistrar.class.getName());

    private final ApplicationContext applicationContext;
    private final ActivityLogHandler activityLogHandler;
    private final List<Logger> instrumentedLoggers = new ArrayList<>();

    public AuditedUseCaseRegistrar(ApplicationContext applicationContext,
                                   ActivityLogHandler activityLogHandler) {
        this.applicationContext = applicationContext;
        this.activityLogHandler = activityLogHandler;
    }

    @Override
    public void afterSingletonsInstantiated() {
        applicationContext.getBeansWithAnnotation(AuditedUseCase.class).values()
                .forEach(this::instrument);

        log.info(() -> "Auditoría centralizada activa en %d caso(s) de uso."
                .formatted(instrumentedLoggers.size()));
    }

    private void instrument(Object bean) {
        Class<?> useCaseClass = AopUtils.getTargetClass(bean);
        AuditedUseCase annotation = AnnotationUtils.findAnnotation(useCaseClass, AuditedUseCase.class);

        if (annotation == null) {
            return;
        }

        String moduleName = annotation.value().isBlank() ? useCaseClass.getSimpleName() : annotation.value();
        Logger useCaseLogger = Logger.getLogger(useCaseClass.getName());

        activityLogHandler.register(useCaseClass.getName(), moduleName);

        // Un contexto anterior pudo dejar su propio manejador colgado de este logger estático.
        removeActivityLogHandlers(useCaseLogger);
        useCaseLogger.addHandler(activityLogHandler);

        // Los mensajes siguen llegando a la consola: la traza en base de datos se suma al
        // registro habitual, no lo reemplaza.
        useCaseLogger.setUseParentHandlers(true);

        instrumentedLoggers.add(useCaseLogger);
    }

    @Override
    public void destroy() {
        instrumentedLoggers.forEach(AuditedUseCaseRegistrar::removeActivityLogHandlers);
        instrumentedLoggers.clear();
    }

    private static void removeActivityLogHandlers(Logger logger) {
        for (Handler handler : logger.getHandlers()) {
            if (handler instanceof ActivityLogHandler) {
                logger.removeHandler(handler);
            }
        }
    }
}
