package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManagePriceListUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryPriceListUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.PriceListSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.PriceList;
import io.github.KevinMitsi.inventories.domain.model.ProductPrice;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PriceListDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.SalesWebMapper;
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

/** Listas de precios y precios por producto (EP-06, RF-29, HU-25). */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Listas de precios", description = "Precios de venta por producto, agrupados en listas.")
public class PriceListController {

    private final ManagePriceListUseCase managePriceListUseCase;
    private final QueryPriceListUseCase queryPriceListUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final SalesWebMapper mapper;

    @PostMapping(value = "/organizations/{organizationId}/price-lists", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "createPriceList", summary = "Crear una lista de precios (RF-29)")
    public ResponseEntity<PriceListDtos.PriceListResponse> createPriceList(
            @PathVariable UUID organizationId,
            @Valid @RequestBody PriceListDtos.CreatePriceListRequest request) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "crear listas de precios");

        PriceList priceList = managePriceListUseCase.createPriceList(mapper.toCommand(organizationId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/price-lists/{id}")
                .buildAndExpand(priceList.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(priceList));
    }

    @GetMapping("/organizations/{organizationId}/price-lists")
    @Operation(operationId = "searchPriceLists", summary = "Listar listas de precios de la organización")
    public PageResponse<PriceListDtos.PriceListResponse> searchPriceLists(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) Boolean active,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar listas de precios");

        PageResult<PriceList> result = queryPriceListUseCase.searchPriceLists(
                new PriceListSearchCriteria(organizationId, active), PageQuery.of(page, size));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/price-lists/{priceListId}")
    @Operation(operationId = "getPriceListById", summary = "Consultar una lista de precios")
    public PriceListDtos.PriceListResponse getPriceList(@PathVariable UUID priceListId) {
        return mapper.toResponse(queryPriceListUseCase.getPriceListById(priceListId));
    }

    @PutMapping(value = "/price-lists/{priceListId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "updatePriceList", summary = "Actualizar una lista de precios",
            description = "Modifica nombre, descripción y vigencia. El código no es modificable.")
    public PriceListDtos.PriceListResponse updatePriceList(
            @PathVariable UUID priceListId,
            @Valid @RequestBody PriceListDtos.UpdatePriceListRequest request) {

        return mapper.toResponse(managePriceListUseCase.updatePriceList(mapper.toCommand(priceListId, request)));
    }

    @PatchMapping("/price-lists/{priceListId}/deactivation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "deactivatePriceList", summary = "Dar de baja una lista de precios")
    public PriceListDtos.PriceListResponse deactivatePriceList(@PathVariable UUID priceListId) {
        return mapper.toResponse(managePriceListUseCase.deactivatePriceList(priceListId));
    }

    @PatchMapping("/price-lists/{priceListId}/activation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "activatePriceList", summary = "Reactivar una lista de precios")
    public PriceListDtos.PriceListResponse activatePriceList(@PathVariable UUID priceListId) {
        return mapper.toResponse(managePriceListUseCase.activatePriceList(priceListId));
    }

    @PatchMapping("/price-lists/{priceListId}/product-prices")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "setProductPrice", summary = "Fijar el precio de un producto en la lista (HU-25)",
            description = "Crea el precio si no existe, o lo reemplaza si ya estaba fijado para esa "
                    + "presentación.")
    public PriceListDtos.ProductPriceResponse setProductPrice(
            @PathVariable UUID priceListId,
            @Valid @RequestBody PriceListDtos.SetProductPriceRequest request) {

        ProductPrice productPrice = managePriceListUseCase.setProductPrice(mapper.toCommand(priceListId, request));
        return mapper.toResponse(productPrice);
    }

    @GetMapping("/price-lists/{priceListId}/product-prices")
    @Operation(operationId = "getProductPrice", summary = "Consultar el precio de un producto en la lista")
    public PriceListDtos.ProductPriceResponse getProductPrice(
            @PathVariable UUID priceListId,
            @RequestParam UUID productId,
            @RequestParam UUID productUnitId) {

        return mapper.toResponse(queryPriceListUseCase.getProductPrice(priceListId, productId, productUnitId));
    }
}
