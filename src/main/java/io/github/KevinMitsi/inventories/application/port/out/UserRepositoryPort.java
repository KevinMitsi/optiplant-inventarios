package io.github.KevinMitsi.inventories.application.port.out;

import io.github.KevinMitsi.inventories.application.port.in.query.UserSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.User;

import java.util.Optional;
import java.util.UUID;

/** Puerto de salida para la persistencia de usuarios. */
public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(UUID id);

    /**
     * Busca por correo para autenticar.
     *
     * <p>El índice único del esquema es por (organización, correo), de modo que en un
     * escenario con varias organizaciones dos usuarios distintos podrían compartir
     * dirección. El sistema opera hoy sobre una sola organización (supuesto S-01), así que
     * la búsqueda por correo es determinista; cuando eso deje de ser cierto, el formulario
     * de acceso tendrá que incorporar un discriminador de organización.
     */
    Optional<User> findByEmail(String email);

    Optional<User> findByOrganizationIdAndEmail(UUID organizationId, String email);

    boolean existsByOrganizationIdAndEmail(UUID organizationId, String email);

    boolean existsById(UUID id);

    /** Lista usuarios filtrados y paginados. */
    PageResult<User> search(UserSearchCriteria criteria, PageQuery pageQuery);

    /**
     * Cuenta los administradores activos de una organización.
     *
     * <p>Lo consume la regla que impide dejar la organización sin ningún administrador:
     * sin ella, dar de baja al último dejaría el sistema sin nadie capaz de gestionar
     * usuarios ni sucursales, y sin forma de recuperarse desde la propia aplicación.
     */
    long countActiveAdmins(UUID organizationId);
}
