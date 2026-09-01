package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.QueryDashboardUseCase;
import io.github.KevinMitsi.inventories.infrastructure.adapter.security.CurrentUserProvider;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.DashboardDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.DashboardWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Dashboard analítico de solo lectura (EP-09, RF-42..RF-47, HU-38..42). Sin agregado de
 * dominio nuevo — decisión de diseño #7 de Fase 5: solo proyecciones sobre datos ya
 * registrados por ventas, catálogo e inventario.
 */
@RestController
@RequestMapping(value = "/api/v1/organizations/{organizationId}/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Indicadores de ventas, rotación de inventario y comparación entre sucursales.")
public class DashboardController {

    /** Ventana por defecto cuando no se indica período (RF-42/43, RF-44): últimos 6 meses. */
    private static final int DEFAULT_PERIOD_MONTHS = 6;

    private final QueryDashboardUseCase queryDashboardUseCase;
    private final CurrentUserProvider currentUserProvider;
    private final DashboardWebMapper mapper;

    @GetMapping("/sales-summary")
    @Operation(operationId = "getSalesSummary", summary = "Volumen de ventas mensual (RF-42/43, HU-38)",
            description = "Ventas confirmadas agrupadas por mes calendario, para comparar el mes actual "
                    + "contra meses anteriores. Sin período explícito, se usan los últimos 6 meses.")
    public List<DashboardDtos.SalesSummaryResponse> getSalesSummary(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar el dashboard de ventas");

        return queryDashboardUseCase
                .getSalesSummary(organizationId, branchId, resolveFrom(from), resolveTo(to))
                .stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/product-rotation")
    @Operation(operationId = "getProductRotation", summary = "Rotación de inventario por producto (RF-44, HU-39)",
            description = "Cantidad vendida por producto en el período, de mayor a menor demanda; los "
                    + "productos sin ventas en el período aparecen al final con cantidad cero. Sin período "
                    + "explícito, se usan los últimos 6 meses.")
    public List<DashboardDtos.ProductRotationResponse> getProductRotation(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        currentUserProvider.requireBelongsToOrganization(organizationId, "consultar la rotación de inventario");

        return queryDashboardUseCase
                .getProductRotation(organizationId, branchId, resolveFrom(from), resolveTo(to))
                .stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/branch-comparison")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "getBranchComparison", summary = "Comparar sucursales (RF-47, HU-42)",
            description = "Ventas confirmadas de los últimos 30 días, valor de inventario y productos en "
                    + "stock crítico, por sucursal. Reservado al administrador general (RN-12).")
    public List<DashboardDtos.BranchComparisonResponse> getBranchComparison(@PathVariable UUID organizationId) {
        currentUserProvider.requireBelongsToOrganization(organizationId, "comparar sucursales");

        return queryDashboardUseCase.getBranchComparison(organizationId).stream()
                .map(mapper::toResponse).toList();
    }

    private static Instant resolveFrom(Instant from) {
        return from != null ? from : Instant.now().minus(DEFAULT_PERIOD_MONTHS * 30L, ChronoUnit.DAYS);
    }

    private static Instant resolveTo(Instant to) {
        return to != null ? to : Instant.now();
    }
}
