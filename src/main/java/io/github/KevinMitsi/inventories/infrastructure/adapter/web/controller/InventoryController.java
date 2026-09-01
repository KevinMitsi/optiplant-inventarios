package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageInventoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryInventoryUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.InventorySearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovement;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.InventoryDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.InventoryWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Saldos, movimientos y stock mínimo por sucursal (EP-04).
 *
 * <p>El registro <em>con</em> documento —compra, venta, transferencia, ajuste formal— no
 * vive aquí: cada módulo postea sus propios movimientos. Este controlador cubre la consulta
 * de saldos e histórico, y el movimiento manual sin documento de origen (HU-12/HU-13).
 */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inventario",
     description = """
             Saldo de cada producto por sucursal (RN-02), su histórico de movimientos y su \
             stock mínimo. El stock jamás cambia sin un movimiento que lo explique (RN-04).""")
public class InventoryController {

    private final ManageInventoryUseCase manageInventoryUseCase;
    private final QueryInventoryUseCase queryInventoryUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final InventoryWebMapper mapper;

    @GetMapping("/branches/{branchId}/inventory")
    @Operation(operationId = "searchInventory", summary = "Consultar el inventario de una sucursal",
            description = """
                    Devuelve los saldos de una sucursal, paginados (HU-11).

                    `lowStockOnly=true` filtra a los que están en o por debajo de su mínimo \
                    configurado, la misma condición que dispara una alerta (HU-40).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de saldos.",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "403", description = "La sucursal no está en el alcance del usuario.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<InventoryDtos.InventoryResponse> searchInventory(
            @PathVariable UUID branchId,

            @Parameter(description = "Solo saldos en o por debajo del mínimo.")
            @RequestParam(required = false) Boolean lowStockOnly,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size,

            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "consultar el inventario");

        PageResult<Inventory> result = queryInventoryUseCase.searchInventory(
                new InventorySearchCriteria(branchId, lowStockOnly),
                PageQuery.of(page, size, sortBy, SortDirection.fromString(sortDirection)));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/branches/{branchId}/inventory/{productId}")
    @Operation(operationId = "getInventory", summary = "Consultar el saldo de un producto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo encontrado.",
                    content = @Content(schema = @Schema(implementation = InventoryDtos.InventoryResponse.class))),
            @ApiResponse(responseCode = "404", description = "El producto nunca tuvo movimientos en esta sucursal.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InventoryDtos.InventoryResponse getInventory(@PathVariable UUID branchId, @PathVariable UUID productId) {
        currentUserProvider.requireCanOperateOnBranch(branchId, "consultar el inventario");
        return mapper.toResponse(queryInventoryUseCase.getByBranchAndProduct(branchId, productId));
    }

    @GetMapping("/branches/{branchId}/inventory/{productId}/movements")
    @Operation(operationId = "getMovementHistory", summary = "Consultar el histórico de movimientos",
            description = "Movimientos de un saldo, del más reciente al más antiguo (HU-14, RN-11).")
    @ApiResponse(responseCode = "200", description = "Página de movimientos.",
            content = @Content(schema = @Schema(implementation = PageResponse.class)))
    public PageResponse<InventoryDtos.InventoryMovementResponse> getMovementHistory(
            @PathVariable UUID branchId,
            @PathVariable UUID productId,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "consultar movimientos de inventario");

        PageResult<InventoryMovement> result = queryInventoryUseCase.getMovementHistory(
                branchId, productId, PageQuery.of(page, size));

        return PageResponse.from(result, mapper::toResponse);
    }

    @PatchMapping(value = "/branches/{branchId}/inventory/{productId}/minimum-stock",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "setMinimumStock", summary = "Configurar el stock mínimo",
            description = "Define el umbral que dispara una alerta de reabastecimiento (HU-15, RF-15).")
    @ApiResponse(responseCode = "200", description = "Stock mínimo actualizado.",
            content = @Content(schema = @Schema(implementation = InventoryDtos.InventoryResponse.class)))
    public InventoryDtos.InventoryResponse setMinimumStock(
            @PathVariable UUID branchId,
            @PathVariable UUID productId,
            @Valid @RequestBody InventoryDtos.SetMinimumStockRequest request) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "configurar el stock mínimo");

        return mapper.toResponse(manageInventoryUseCase.setMinimumStock(
                mapper.toCommand(branchId, productId, request)));
    }

    @PostMapping(value = "/branches/{branchId}/inventory/entries", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "registerInventoryEntry", summary = "Registrar una entrada manual",
            description = """
                    Entrada sin documento de origen: devolución, hallazgo u otro ingreso libre \
                    (HU-12). Se postea como movimiento `RETURN_IN`. Compras, transferencias y \
                    ajustes formales tienen su propio flujo y no pasan por aquí.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Movimiento registrado.",
                    content = @Content(schema = @Schema(implementation = InventoryDtos.InventoryMovementResponse.class))),
            @ApiResponse(responseCode = "404", description = "La sucursal o el producto no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InventoryDtos.InventoryMovementResponse registerEntry(
            @PathVariable UUID branchId,
            @Valid @RequestBody InventoryDtos.RegisterInventoryMovementRequest request) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "registrar una entrada de inventario");
        UUID userId = currentUserProvider.requireUserId();

        InventoryMovement movement = manageInventoryUseCase.registerEntry(
                mapper.toEntryCommand(branchId, request, userId));

        return mapper.toResponse(movement);
    }

    @PostMapping(value = "/branches/{branchId}/inventory/exits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER', 'INVENTORY_OPERATOR')")
    @Operation(operationId = "registerInventoryExit", summary = "Registrar una salida manual",
            description = """
                    Salida sin documento de origen: merma u otra baja libre (HU-13). Se \
                    postea como movimiento `LOSS_OUT`. Falla si no hay stock suficiente.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Movimiento registrado.",
                    content = @Content(schema = @Schema(implementation = InventoryDtos.InventoryMovementResponse.class))),
            @ApiResponse(responseCode = "404", description = "La sucursal o el producto no existen.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Stock insuficiente (RN-03).",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public InventoryDtos.InventoryMovementResponse registerExit(
            @PathVariable UUID branchId,
            @Valid @RequestBody InventoryDtos.RegisterInventoryMovementRequest request) {

        currentUserProvider.requireCanOperateOnBranch(branchId, "registrar una salida de inventario");
        UUID userId = currentUserProvider.requireUserId();

        InventoryMovement movement = manageInventoryUseCase.registerExit(
                mapper.toExitCommand(branchId, request, userId));

        return mapper.toResponse(movement);
    }
}
