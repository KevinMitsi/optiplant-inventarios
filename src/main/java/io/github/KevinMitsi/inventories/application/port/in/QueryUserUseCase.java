package io.github.KevinMitsi.inventories.application.port.in;

import io.github.KevinMitsi.inventories.application.port.in.query.UserSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.User;

import java.util.UUID;

/** Consulta de usuarios. */
public interface QueryUserUseCase {

    /**
     * Recupera un usuario por identificador.
     *
     * @throws io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException
     *         si no existe
     */
    User getUserById(UUID userId);

    /** Lista usuarios filtrados y paginados. */
    PageResult<User> searchUsers(UserSearchCriteria criteria, PageQuery pageQuery);
}
