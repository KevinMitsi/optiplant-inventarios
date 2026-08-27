package io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper;

import io.github.KevinMitsi.inventories.application.port.in.command.CreateBranchCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateBranchCommand;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.BranchResponse;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.CreateBranchRequest;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.UpdateBranchRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Traduce entre los DTO del contrato HTTP y los tipos de la capa de aplicación.
 *
 * <p>Es la segunda costura del sistema, simétrica a la del adaptador de persistencia:
 * <pre>
 *   Request  a  Command   (entra)
 *   Domain   a  Response  (sale)
 * </pre>
 *
 * <p>Nada de lo que entra por HTTP llega al caso de uso con su forma original, y nada del
 * dominio sale tal cual hacia el cliente. Ese doble filtro es lo que permite que el
 * contrato público y el modelo interno cambien de forma independiente.
 *
 * <p>MapStruct genera el código al compilar: sin reflexión en ejecución, y un campo que
 * deje de tener correspondencia rompe la compilación en lugar de aparecer como nulo en
 * producción.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BranchWebMapper {

    /**
     * Combina la organización, que viaja en la ruta, con el cuerpo de la petición.
     *
     * <p>La organización se toma de la URL y nunca del cuerpo: identifica la colección
     * sobre la que se crea el recurso y, una vez haya seguridad, se contrastará con la del
     * usuario autenticado. Aceptarla en el cuerpo permitiría a un cliente intentar crear
     * sucursales en una organización ajena.
     */
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "code", source = "request.code")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "addressLine", source = "request.addressLine")
    @Mapping(target = "city", source = "request.city")
    @Mapping(target = "countryCode", source = "request.countryCode")
    @Mapping(target = "phone", source = "request.phone")
    CreateBranchCommand toCommand(java.util.UUID organizationId, CreateBranchRequest request);

    /** Combina el identificador de la ruta con los datos modificables del cuerpo. */
    @Mapping(target = "branchId", source = "branchId")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "addressLine", source = "request.addressLine")
    @Mapping(target = "city", source = "request.city")
    @Mapping(target = "countryCode", source = "request.countryCode")
    @Mapping(target = "phone", source = "request.phone")
    UpdateBranchCommand toCommand(java.util.UUID branchId, UpdateBranchRequest request);

    /** Modelo de dominio a representación pública. */
    BranchResponse toResponse(Branch branch);
}
