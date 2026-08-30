package io.github.KevinMitsi.inventories.infrastructure.config;

import io.github.KevinMitsi.inventories.domain.usecase.AdminBootstrapUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ejecuta {@link AdminBootstrapUseCase} en cada arranque, dentro de una transacción propia.
 *
 * <p>Es el único punto de infraestructura que dispara el caso de uso: como el resto de
 * {@code application.service}, la frontera transaccional vive aquí, no en el caso de uso
 * (pura lógica Java, sin Spring).
 */
@Component
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapUseCase adminBootstrapUseCase;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapRunner(AdminBootstrapUseCase adminBootstrapUseCase, AdminBootstrapProperties properties) {
        this.adminBootstrapUseCase = adminBootstrapUseCase;
        this.properties = properties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        adminBootstrapUseCase.bootstrapAdmin(properties.email(), properties.password());
    }
}
