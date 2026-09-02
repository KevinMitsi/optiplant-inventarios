package io.github.KevinMitsi.inventories.domain.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un caso de uso cuyos registros deben quedar en la traza de auditoría.
 *
 * <p>Es el equivalente propio de {@code @Slf4j}: no genera nada en compilación, sino que
 * declara una intención que la infraestructura recoge. Al arrancar, el registrador de
 * infraestructura busca los beans anotados y engancha un manejador de
 * {@code java.util.logging} al {@code Logger} de cada clase. A partir de ahí, cada
 * {@code log.info(...)} que ya escribe el caso de uso —sin una sola línea añadida— acaba
 * también en la tabla {@code activity_log} con fecha, usuario, rol y operación.
 *
 * <p>Vive en {@code domain} y no en {@code infrastructure} por una razón dura: el dominio
 * no puede depender de infraestructura, y la anotación tiene que estar en el mismo lado
 * que las clases que la llevan. Que sea una anotación propia, sin dependencias más allá de
 * {@code java.lang.annotation}, es justo lo que permite ponerla ahí sin contaminar la capa;
 * quien la interpreta —el manejador, el servicio y el repositorio— sí vive en
 * infraestructura, que es donde debe estar.
 *
 * <p>No se anota {@code ActivityLogUseCase}: escribir un registro provocaría otro registro,
 * y el ciclo no tendría fin.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditedUseCase {

    /**
     * Nombre del módulo bajo el que agrupar la traza. Si se deja vacío se usa el nombre
     * simple de la clase, que es lo que quiere el listado en la inmensa mayoría de casos.
     */
    String value() default "";
}
