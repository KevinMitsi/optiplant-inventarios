package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.exception.OperationNotPermittedException;
import io.github.KevinMitsi.inventories.application.port.in.ManageTransferIssueUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryTransferIssueUseCase;
import io.github.KevinMitsi.inventories.domain.model.TransferIssue;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.AuthenticatedUser;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.TransferIssueDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.TransferWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Incidencias de recepción de una transferencia (ENTITIES.md §15, HU-33): faltantes
 * detectados automáticamente al recibir menos de lo despachado (RN-10).
 *
 * <p>Resolver exige {@link io.github.KevinMitsi.inventories.domain.model.RoleCode#canResolveTransferIssues()}:
 * decidir cómo se cierra un faltante (reenvío, ajuste, reclamación) es una decisión de
 * supervisión, no una tarea de ejecución.
 */
@RestController
@RequestMapping(value = "/api/v1/transfers/{transferId}/issues", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Incidencias de transferencia", description = "Faltantes detectados al recibir una transferencia.")
public class TransferIssueController {

    private final ManageTransferIssueUseCase manageTransferIssueUseCase;
    private final QueryTransferIssueUseCase queryTransferIssueUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final TransferWebMapper mapper;

    @GetMapping
    @Operation(operationId = "listTransferIssues", summary = "Incidencias de una transferencia",
            description = "Todas las incidencias de sus líneas, resueltas o no.")
    public List<TransferIssueDtos.TransferIssueResponse> listIssues(@PathVariable UUID transferId) {
        return queryTransferIssueUseCase.listIssuesForTransfer(transferId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @PostMapping(value = "/{issueId}/resolution", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "resolveTransferIssue", summary = "Resolver una incidencia (HU-33)",
            description = "Deja constancia de cómo se resolvió (reenvío, ajuste o reclamación). "
                    + "No ejecuta esa resolución automáticamente. Si era la última incidencia "
                    + "pendiente, la transferencia pasa a CLOSED.")
    public TransferIssueDtos.TransferIssueResponse resolveIssue(
            @PathVariable UUID transferId,
            @PathVariable UUID issueId,
            @Valid @RequestBody TransferIssueDtos.ResolveTransferIssueRequest request) {

        AuthenticatedUser user = requireCanResolveTransferIssues();

        TransferIssue issue = manageTransferIssueUseCase.resolveIssue(
                mapper.toCommand(transferId, issueId, user.userId(), request));

        return mapper.toResponse(issue);
    }

    private AuthenticatedUser requireCanResolveTransferIssues() {
        AuthenticatedUser user = currentUserProvider.require();
        if (!user.role().canResolveTransferIssues()) {
            throw new OperationNotPermittedException("resolver una incidencia de transferencia",
                    "el rol asignado no autoriza a decidir cómo se cierra un faltante");
        }
        return user;
    }
}
