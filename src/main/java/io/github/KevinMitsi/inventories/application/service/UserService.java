package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageUserUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryUserUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.ChangePasswordCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateUserCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReassignUserCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateUserProfileCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.UserSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.User;
import io.github.KevinMitsi.inventories.domain.usecase.UserUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Primary
@Service
@Transactional(rollbackFor = Exception.class)
public class UserService implements ManageUserUseCase, QueryUserUseCase {

    private final UserUseCase useCase;

    public UserService(UserUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public User createUser(CreateUserCommand command) {
        return useCase.createUser(command);
    }

    @Override
    public User updateProfile(UpdateUserProfileCommand command) {
        return useCase.updateProfile(command);
    }

    @Override
    public User reassign(ReassignUserCommand command) {
        return useCase.reassign(command);
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        useCase.changePassword(command);
    }

    @Override
    public User deactivateUser(UUID userId) {
        return useCase.deactivateUser(userId);
    }

    @Override
    public User activateUser(UUID userId) {
        return useCase.activateUser(userId);
    }

    @Override
    public User getUserById(UUID userId) {
        return useCase.getUserById(userId);
    }

    @Override
    public PageResult<User> searchUsers(UserSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchUsers(criteria, pageQuery);
    }
}
