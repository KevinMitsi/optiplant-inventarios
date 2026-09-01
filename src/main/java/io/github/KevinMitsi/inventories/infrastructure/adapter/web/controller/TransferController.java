package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.exception.OperationNotPermittedException;
import io.github.KevinMitsi.inventories.application.port.in.ManageTransferUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryTransferUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.TransferSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Transfer;
import io.github.KevinMitsi.inventories.domain.model.TransferStatus;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.AuthenticatedUser;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.TransferDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.TransferWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Transferencias de mercancía entre sucursales (EP-07): solicitar, aprobar, preparar,
 * despachar y recibir.
 *
 * <p>Aprobar y cancelar exigen el rol de supervisión ({@link io.github.KevinMitsi.inventories.domain.model.RoleCode#canApproveTransfers()}):
 * comprometer el stock de origen no es una tarea de ejecución. El resto de operaciones
 * (solicitar, preparar, despachar, recibir) están abiertas a cualquier rol operativo.
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transferencias", description = "Traslado de mercancía entre sucursales.")
public class TransferController {

    private final ManageTransferUseCase manageTransferUseCase;
    private final QueryTransferUseCase queryTransferUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final TransferWebMapper mapper;

    @PostMapping(value = "/branches/{originBranchId}/transfers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "createTransfer", summary = "Solicitar una transferencia (HU-27)",
            description = "El origen pide reponer stock desde otra sucursal. Origen y destino "
                    + "deben ser distintos (RN-07).")
    public ResponseEntity<TransferDtos.TransferResponse> createTransfer(
            @PathVariable UUID originBranchId,
            @Valid @RequestBody TransferDtos.CreateTransferRequest request) {

        currentUserProvider.requireCanOperateOnBranch(originBranchId, "solicitar una transferencia");
        UUID userId = currentUserProvider.requireUserId();

        Transfer transfer = manageTransferUseCase.createTransfer(
                mapper.toCommand(originBranchId, userId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/transfers/{id}")
                .buildAndExpand(transfer.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(transfer));
    }

    @GetMapping("/branches/{branchId}/transfers")
    @Operation(operationId = "searchTransfers", summary = "Transferencias en curso vistas desde una sucursal",
            description = "Incluye tanto lo que la sucursal solicitó como lo que le están por "
                    + "enviar (RF-46, HU-35, HU-41).")
    public PageResponse<TransferDtos.TransferResponse> searchTransfers(
            @PathVariable UUID branchId,
            @RequestParam(required = false) String status,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "consultar transferencias");

        TransferStatus statusFilter = status == null || status.isBlank() ? null : TransferStatus.fromString(status);

        PageResult<Transfer> result = queryTransferUseCase.searchTransfers(
                new TransferSearchCriteria(branchId, statusFilter), PageQuery.of(page, size));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/transfers/{transferId}")
    @Operation(operationId = "getTransferById", summary = "Consultar una transferencia")
    public TransferDtos.TransferResponse getTransfer(@PathVariable UUID transferId) {
        return mapper.toResponse(queryTransferUseCase.getTransferById(transferId));
    }

    @PatchMapping(value = "/transfers/{transferId}/approval", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "approveTransfer", summary = "Aprobar una transferencia solicitada (HU-29)",
            description = "Cada línea se aprueba por la cantidad indicada, o por la solicitada "
                    + "si no se indica ninguna. Comprometer stock de origen es una decisión de "
                    + "supervisión, fuera del alcance del operador.")
    public TransferDtos.TransferResponse approveTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody TransferDtos.ApproveTransferRequest request) {

        AuthenticatedUser user = requireCanApproveTransfers();

        Transfer transfer = manageTransferUseCase.approveTransfer(
                mapper.toCommand(transferId, user.userId(), request));

        return mapper.toResponse(transfer);
    }

    @PostMapping(value = "/transfers/{transferId}/logistics-assignment", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "assignTransferLogistics", summary = "Asignar transportista y ruta (Fase 5)",
            description = "Solo antes de despachar. La ruta debe conectar el origen y el "
                    + "destino de la transferencia.")
    public TransferDtos.TransferResponse assignLogistics(
            @PathVariable UUID transferId,
            @Valid @RequestBody TransferDtos.AssignTransferLogisticsRequest request) {

        Transfer transfer = manageTransferUseCase.assignLogistics(mapper.toCommand(transferId, request));
        return mapper.toResponse(transfer);
    }

    @PostMapping("/transfers/{transferId}/preparation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "startTransferPreparation", summary = "Iniciar la preparación de una transferencia aprobada")
    public TransferDtos.TransferResponse startPreparation(@PathVariable UUID transferId) {
        UUID userId = currentUserProvider.requireUserId();
        return mapper.toResponse(manageTransferUseCase.startPreparation(transferId, userId));
    }

    @PostMapping(value = "/transfers/{transferId}/dispatch", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "dispatchTransfer", summary = "Despachar una transferencia en preparación",
            description = "Descuenta inventario de origen mediante TRANSFER_OUT, validando "
                    + "stock disponible (RN-08).")
    public TransferDtos.TransferResponse dispatchTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody TransferDtos.DispatchTransferRequest request) {

        UUID userId = currentUserProvider.requireUserId();

        Transfer transfer = manageTransferUseCase.dispatchTransfer(
                mapper.toCommand(transferId, userId, request));

        return mapper.toResponse(transfer);
    }

    @PostMapping(value = "/transfers/{transferId}/reception", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "receiveTransfer", summary = "Confirmar la recepción de una transferencia",
            description = "Aumenta inventario de destino mediante TRANSFER_IN por lo realmente "
                    + "recibido (RN-09). Si alguna línea llegó incompleta, abre una incidencia "
                    + "por el faltante (RN-10) y la transferencia queda PARTIALLY_RECEIVED.")
    public TransferDtos.TransferResponse receiveTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody TransferDtos.ReceiveTransferRequest request) {

        UUID userId = currentUserProvider.requireUserId();

        Transfer transfer = manageTransferUseCase.receiveTransfer(
                mapper.toCommand(transferId, userId, request));

        return mapper.toResponse(transfer);
    }

    @PostMapping("/transfers/{transferId}/cancellation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "cancelTransfer", summary = "Cancelar una transferencia",
            description = "Solo antes de despachar: después ya hay stock de origen comprometido.")
    public TransferDtos.TransferResponse cancelTransfer(@PathVariable UUID transferId) {
        UUID userId = currentUserProvider.requireUserId();
        return mapper.toResponse(manageTransferUseCase.cancelTransfer(transferId, userId));
    }

    private AuthenticatedUser requireCanApproveTransfers() {
        AuthenticatedUser user = currentUserProvider.require();
        if (!user.role().canApproveTransfers()) {
            throw new OperationNotPermittedException("aprobar una transferencia",
                    "el rol asignado no autoriza a comprometer stock de origen");
        }
        return user;
    }
}
