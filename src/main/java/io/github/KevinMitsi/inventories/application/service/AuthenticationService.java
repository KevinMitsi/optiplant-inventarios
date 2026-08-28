package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.AuthenticateUserUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.AuthenticationCommand;
import io.github.KevinMitsi.inventories.application.port.in.result.AuthenticationResult;
import io.github.KevinMitsi.inventories.domain.usecase.AuthenticationUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class AuthenticationService implements AuthenticateUserUseCase {

    private final AuthenticationUseCase useCase;

    public AuthenticationService(AuthenticationUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public AuthenticationResult authenticate(AuthenticationCommand command) {
        return useCase.authenticate(command);
    }

    @Override
    public AuthenticationResult refresh(String refreshToken) {
        return useCase.refresh(refreshToken);
    }
}
