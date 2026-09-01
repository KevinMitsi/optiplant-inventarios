package io.github.KevinMitsi.inventories.infrastructure.adapter.web.mapper;

import io.github.KevinMitsi.inventories.application.port.in.command.ApproveTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.AssignTransferLogisticsCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.DispatchTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReceiveTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ResolveTransferIssueCommand;
import io.github.KevinMitsi.inventories.domain.model.Transfer;
import io.github.KevinMitsi.inventories.domain.model.TransferIssue;
import io.github.KevinMitsi.inventories.domain.model.TransferItem;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.TransferDtos;
import io.github.KevinMitsi.inventories.infrastructure.adapter.web.dto.TransferIssueDtos;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Traduce transferencias y sus incidencias entre el contrato HTTP y la capa de aplicación. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TransferWebMapper {

    default CreateTransferCommand toCommand(UUID originBranchId, UUID requestedBy,
                                            TransferDtos.CreateTransferRequest request) {
        List<CreateTransferCommand.Item> items = request.items().stream()
                .map(item -> new CreateTransferCommand.Item(item.productId(), item.quantity()))
                .toList();

        return new CreateTransferCommand(originBranchId, request.destinationBranchId(), requestedBy,
                request.transferNumber(), request.priority(), request.notes(), items);
    }

    default AssignTransferLogisticsCommand toCommand(UUID transferId,
                                                      TransferDtos.AssignTransferLogisticsRequest request) {
        return new AssignTransferLogisticsCommand(transferId, request.carrierId(), request.routeId(),
                request.estimatedArrivalAt());
    }

    default ApproveTransferCommand toCommand(UUID transferId, UUID approvedBy,
                                             TransferDtos.ApproveTransferRequest request) {
        return new ApproveTransferCommand(transferId, approvedBy, toItemQuantities(request.approvedQuantities()));
    }

    default DispatchTransferCommand toCommand(UUID transferId, UUID userId,
                                              TransferDtos.DispatchTransferRequest request) {
        return new DispatchTransferCommand(transferId, userId,
                request.shippedQuantities() == null ? List.of() : request.shippedQuantities().stream()
                        .map(item -> new DispatchTransferCommand.ItemQuantity(item.itemId(), item.quantity()))
                        .toList());
    }

    default ReceiveTransferCommand toCommand(UUID transferId, UUID userId,
                                             TransferDtos.ReceiveTransferRequest request) {
        return new ReceiveTransferCommand(transferId, userId,
                request.receivedQuantities() == null ? List.of() : request.receivedQuantities().stream()
                        .map(item -> new ReceiveTransferCommand.ItemQuantity(item.itemId(), item.quantity()))
                        .toList());
    }

    default List<ApproveTransferCommand.ItemQuantity> toItemQuantities(
            List<TransferDtos.ItemQuantityRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(item -> new ApproveTransferCommand.ItemQuantity(item.itemId(), item.quantity()))
                .toList();
    }

    default ResolveTransferIssueCommand toCommand(UUID transferId, UUID issueId, UUID resolvedBy,
                                                  TransferIssueDtos.ResolveTransferIssueRequest request) {
        return new ResolveTransferIssueCommand(transferId, issueId, resolvedBy, request.resolutionType());
    }

    TransferDtos.TransferResponse toResponse(Transfer transfer);

    TransferDtos.TransferResponse.ItemResponse toResponse(TransferItem item);

    TransferIssueDtos.TransferIssueResponse toResponse(TransferIssue issue);

    default BigDecimal map(io.github.KevinMitsi.inventories.domain.model.Quantity quantity) {
        return quantity == null ? null : quantity.value();
    }
}
