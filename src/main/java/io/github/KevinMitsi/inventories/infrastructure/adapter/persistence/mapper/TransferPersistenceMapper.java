package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.mapper;

import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.domain.model.Transfer;
import io.github.KevinMitsi.inventories.domain.model.TransferIssue;
import io.github.KevinMitsi.inventories.domain.model.TransferIssueResolution;
import io.github.KevinMitsi.inventories.domain.model.TransferIssueType;
import io.github.KevinMitsi.inventories.domain.model.TransferItem;
import io.github.KevinMitsi.inventories.domain.model.TransferPriority;
import io.github.KevinMitsi.inventories.domain.model.TransferStatus;
import io.github.KevinMitsi.inventories.domain.model.TransferStatusHistory;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.TransferIssueJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.TransferItemJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.TransferJpaEntity;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity.TransferStatusHistoryJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduce transferencias, sus líneas, su histórico de estados y sus incidencias entre
 * dominio y entidades persistentes. Plana y no MapStruct, mismo motivo que
 * {@link PurchasingPersistenceMapper}: cada agregado se reconstruye con un factory que
 * revalida invariantes.
 */
@Component
public class TransferPersistenceMapper {

    public TransferJpaEntity toEntity(Transfer transfer) {
        if (transfer == null) {
            return null;
        }
        TransferJpaEntity entity = TransferJpaEntity.builder()
                .id(transfer.getId())
                .transferNumber(transfer.getTransferNumber())
                .originBranchId(transfer.getOriginBranchId())
                .destinationBranchId(transfer.getDestinationBranchId())
                .requestedBy(transfer.getRequestedBy())
                .approvedBy(transfer.getApprovedBy())
                .status(transfer.getStatus().name())
                .priority(transfer.getPriority().name())
                .carrierId(transfer.getCarrierId())
                .routeId(transfer.getRouteId())
                .requestedAt(transfer.getRequestedAt())
                .approvedAt(transfer.getApprovedAt())
                .shippedAt(transfer.getShippedAt())
                .estimatedArrivalAt(transfer.getEstimatedArrivalAt())
                .receivedAt(transfer.getReceivedAt())
                .notes(transfer.getNotes())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .build();

        entity.replaceItems(transfer.getItems().stream().map(this::toItemEntity).toList());
        return entity;
    }

    public Transfer toDomain(TransferJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<TransferItem> items = entity.getItems().stream().map(this::toItemDomain).toList();
        return Transfer.reconstitute(entity.getId(), entity.getTransferNumber(), entity.getOriginBranchId(),
                entity.getDestinationBranchId(), entity.getRequestedBy(), TransferStatus.fromString(entity.getStatus()),
                TransferPriority.fromString(entity.getPriority()), entity.getRequestedAt(), entity.getApprovedBy(),
                entity.getApprovedAt(), entity.getCarrierId(), entity.getRouteId(), entity.getShippedAt(),
                entity.getEstimatedArrivalAt(), entity.getReceivedAt(), entity.getNotes(), items,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public TransferItemJpaEntity toItemEntity(TransferItem item) {
        if (item == null) {
            return null;
        }
        return TransferItemJpaEntity.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .requestedQuantity(item.getRequestedQuantity().value())
                .approvedQuantity(quantityValue(item.getApprovedQuantity()))
                .shippedQuantity(quantityValue(item.getShippedQuantity()))
                .receivedQuantity(quantityValue(item.getReceivedQuantity()))
                .build();
    }

    public TransferItem toItemDomain(TransferItemJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return TransferItem.reconstitute(entity.getId(), entity.getProductId(),
                Quantity.of(entity.getRequestedQuantity()), quantityOrNull(entity.getApprovedQuantity()),
                quantityOrNull(entity.getShippedQuantity()), quantityOrNull(entity.getReceivedQuantity()));
    }

    public TransferStatusHistoryJpaEntity toEntity(TransferStatusHistory history) {
        if (history == null) {
            return null;
        }
        return TransferStatusHistoryJpaEntity.builder()
                .id(history.getId())
                .transferId(history.getTransferId())
                .status(history.getStatus().name())
                .changedBy(history.getChangedBy())
                .changedAt(history.getChangedAt())
                .notes(history.getNotes())
                .build();
    }

    public TransferStatusHistory toDomain(TransferStatusHistoryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return TransferStatusHistory.reconstitute(entity.getId(), entity.getTransferId(),
                TransferStatus.fromString(entity.getStatus()), entity.getChangedBy(), entity.getChangedAt(),
                entity.getNotes());
    }

    public TransferIssueJpaEntity toEntity(TransferIssue issue) {
        if (issue == null) {
            return null;
        }
        return TransferIssueJpaEntity.builder()
                .id(issue.getId())
                .transferItemId(issue.getTransferItemId())
                .issueType(issue.getIssueType().name())
                .resolutionType(issue.getResolutionType() == null ? null : issue.getResolutionType().name())
                .quantity(issue.getQuantity().value())
                .description(issue.getDescription())
                .reportedBy(issue.getReportedBy())
                .reportedAt(issue.getReportedAt())
                .resolvedBy(issue.getResolvedBy())
                .resolvedAt(issue.getResolvedAt())
                .build();
    }

    public TransferIssue toDomain(TransferIssueJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        TransferIssueResolution resolution = entity.getResolutionType() == null
                ? null : TransferIssueResolution.fromString(entity.getResolutionType());
        return TransferIssue.reconstitute(entity.getId(), entity.getTransferItemId(),
                TransferIssueType.fromString(entity.getIssueType()), Quantity.of(entity.getQuantity()),
                entity.getDescription(), entity.getReportedBy(), entity.getReportedAt(), resolution,
                entity.getResolvedBy(), entity.getResolvedAt());
    }

    private java.math.BigDecimal quantityValue(Quantity quantity) {
        return quantity == null ? null : quantity.value();
    }

    private Quantity quantityOrNull(java.math.BigDecimal value) {
        return value == null ? null : Quantity.of(value);
    }
}
