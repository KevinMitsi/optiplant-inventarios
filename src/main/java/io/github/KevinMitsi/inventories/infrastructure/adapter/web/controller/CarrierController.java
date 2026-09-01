package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.ManageCarrierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryCarrierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.query.CarrierSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Carrier;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.SortDirection;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CarrierDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PageResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.LogisticsWebMapper;
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

/** Transportistas contratados para mover mercancía entre sucursales (EP-08, HU-36/37). */
@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transportistas", description = "Transportistas contratados para las transferencias.")
public class CarrierController {

    private final ManageCarrierUseCase manageCarrierUseCase;
    private final QueryCarrierUseCase queryCarrierUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final LogisticsWebMapper mapper;

    @PostMapping(value = "/organizations/{organizationId}/carriers", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "createCarrier", summary = "Registrar un transportista")
    public ResponseEntity<CarrierDtos.CarrierResponse> createCarrier(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CarrierDtos.CreateCarrierRequest request) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "crear transportistas");

        Carrier carrier = manageCarrierUseCase.createCarrier(mapper.toCommand(organizationId, request));

        URI location = UriComponentsBuilder.fromPath("/api/v1/carriers/{id}")
                .buildAndExpand(carrier.getId()).toUri();

        return ResponseEntity.created(location).body(mapper.toResponse(carrier));
    }

    @GetMapping("/organizations/{organizationId}/carriers")
    @Operation(operationId = "searchCarriers", summary = "Listar transportistas")
    public PageResponse<CarrierDtos.CarrierResponse> searchCarriers(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) Boolean active,

            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "El número de página no puede ser negativo.") int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "El tamaño de página debe ser al menos 1.")
            @Max(value = 100, message = "El tamaño de página no puede superar {value}.") int size,

            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar transportistas");

        PageResult<Carrier> result = queryCarrierUseCase.searchCarriers(
                new CarrierSearchCriteria(organizationId, text, active),
                PageQuery.of(page, size, sortBy, SortDirection.fromString(sortDirection)));

        return PageResponse.from(result, mapper::toResponse);
    }

    @GetMapping("/carriers/{carrierId}")
    @Operation(operationId = "getCarrierById", summary = "Consultar un transportista")
    public CarrierDtos.CarrierResponse getCarrier(@PathVariable UUID carrierId) {
        return mapper.toResponse(queryCarrierUseCase.getCarrierById(carrierId));
    }

    @PutMapping(value = "/carriers/{carrierId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "updateCarrier", summary = "Actualizar un transportista")
    public CarrierDtos.CarrierResponse updateCarrier(
            @PathVariable UUID carrierId,
            @Valid @RequestBody CarrierDtos.UpdateCarrierRequest request) {

        return mapper.toResponse(manageCarrierUseCase.updateCarrier(mapper.toCommand(carrierId, request)));
    }

    @PatchMapping("/carriers/{carrierId}/deactivation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "deactivateCarrier", summary = "Dar de baja un transportista")
    public CarrierDtos.CarrierResponse deactivateCarrier(@PathVariable UUID carrierId) {
        return mapper.toResponse(manageCarrierUseCase.deactivateCarrier(carrierId));
    }

    @PatchMapping("/carriers/{carrierId}/activation")
    @PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
    @Operation(operationId = "activateCarrier", summary = "Reactivar un transportista")
    public CarrierDtos.CarrierResponse activateCarrier(@PathVariable UUID carrierId) {
        return mapper.toResponse(manageCarrierUseCase.activateCarrier(carrierId));
    }
}
