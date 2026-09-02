package io.github.KevinMitsi.inventories.infrastructure.adapter.web.controller;

import io.github.KevinMitsi.inventories.application.port.in.QueryUnitOfMeasureUseCase;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ApiErrorResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.ProductDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper.CatalogWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/units-of-measure", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Unidades de medida",
     description = "Catálogo global de unidades. Es compartido por todas las organizaciones "
             + "y es donde cada producto elige la unidad en la que se cuenta su stock.")
public class UnitOfMeasureController {

    private final QueryUnitOfMeasureUseCase queryUnitOfMeasureUseCase;
    private final CatalogWebMapper mapper;

    @GetMapping
    @Operation(operationId = "listUnitsOfMeasure", summary = "Listar las unidades disponibles",
            description = "Devuelve el catálogo completo, ordenado por código. No se pagina "
                    + "porque es un conjunto pequeño y estable que el cliente suele cachear.")
    @ApiResponse(responseCode = "200", description = "Unidades de medida.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ProductDtos.UnitOfMeasureResponse.class))))
    public List<ProductDtos.UnitOfMeasureResponse> listUnits() {
        return queryUnitOfMeasureUseCase.getAllUnits().stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{unitId}")
    @Operation(operationId = "getUnitOfMeasureById", summary = "Consultar una unidad de medida")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unidad encontrada.",
                    content = @Content(schema = @Schema(implementation = ProductDtos.UnitOfMeasureResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe una unidad con ese identificador.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDtos.UnitOfMeasureResponse getUnitById(@PathVariable UUID unitId) {
        return mapper.toResponse(queryUnitOfMeasureUseCase.getUnitById(unitId));
    }
}
