package io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper;

import io.github.KevinMitsi.inventories.application.port.in.command.CreatePriceListCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateSaleCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.SetProductPriceCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdatePriceListCommand;
import io.github.KevinMitsi.inventories.domain.model.PriceList;
import io.github.KevinMitsi.inventories.domain.model.ProductPrice;
import io.github.KevinMitsi.inventories.domain.model.Sale;
import io.github.KevinMitsi.inventories.domain.model.SaleItem;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.PriceListDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.SaleDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Traduce listas de precios, precios y ventas entre el contrato HTTP y la capa de aplicación. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SalesWebMapper {

    default CreatePriceListCommand toCommand(UUID organizationId, PriceListDtos.CreatePriceListRequest request) {
        return new CreatePriceListCommand(organizationId, request.code(), request.name(), request.description(),
                request.validFrom(), request.validUntil());
    }

    default UpdatePriceListCommand toCommand(UUID priceListId, PriceListDtos.UpdatePriceListRequest request) {
        return new UpdatePriceListCommand(priceListId, request.name(), request.description(),
                request.validFrom(), request.validUntil());
    }

    default SetProductPriceCommand toCommand(UUID priceListId, PriceListDtos.SetProductPriceRequest request) {
        return new SetProductPriceCommand(priceListId, request.productId(), request.productUnitId(),
                request.price());
    }

    PriceListDtos.PriceListResponse toResponse(PriceList priceList);

    PriceListDtos.ProductPriceResponse toResponse(ProductPrice productPrice);

    default CreateSaleCommand toCommand(UUID branchId, UUID createdBy, SaleDtos.CreateSaleRequest request) {
        List<CreateSaleCommand.Item> items = request.items().stream()
                .map(this::toItem)
                .toList();

        return new CreateSaleCommand(branchId, createdBy, request.priceListId(), request.saleNumber(),
                request.saleDate(), request.notes(), items);
    }

    default CreateSaleCommand.Item toItem(SaleDtos.CreateSaleRequest.ItemRequest item) {
        return new CreateSaleCommand.Item(item.productId(), item.productUnitId(), item.quantity(),
                item.unitPrice(), item.discountPercentage());
    }

    @Mapping(target = "total", expression = "java(map(sale.total()))")
    SaleDtos.SaleResponse toResponse(Sale sale);

    SaleDtos.SaleResponse.ItemResponse toResponse(SaleItem item);

    default BigDecimal map(io.github.KevinMitsi.inventories.domain.model.Quantity quantity) {
        return quantity == null ? null : quantity.value();
    }

    default BigDecimal map(io.github.KevinMitsi.inventories.domain.model.Money money) {
        return money == null ? null : money.amount();
    }

    default BigDecimal map(io.github.KevinMitsi.inventories.domain.model.Percentage percentage) {
        return percentage == null ? null : percentage.value();
    }
}
