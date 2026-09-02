package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageTransferUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryTransferUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.ApproveTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.AssignTransferLogisticsCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.DispatchTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.ReceiveTransferCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.TransferSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.CarrierRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.LogisticsRouteRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferIssueRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferStatusHistoryRepositoryPort;
import io.github.KevinMitsi.inventories.domain.annotation.AuditedUseCase;
import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;
import io.github.KevinMitsi.inventories.domain.model.Inventory;
import io.github.KevinMitsi.inventories.domain.model.InventoryMovementType;
import io.github.KevinMitsi.inventories.domain.model.LogisticsRoute;
import io.github.KevinMitsi.inventories.domain.model.Money;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Product;
import io.github.KevinMitsi.inventories.domain.model.Quantity;
import io.github.KevinMitsi.inventories.domain.model.Transfer;
import io.github.KevinMitsi.inventories.domain.model.TransferIssue;
import io.github.KevinMitsi.inventories.domain.model.TransferIssueType;
import io.github.KevinMitsi.inventories.domain.model.TransferItem;
import io.github.KevinMitsi.inventories.domain.model.TransferPriority;
import io.github.KevinMitsi.inventories.domain.model.TransferStatusHistory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@AuditedUseCase
public class TransferUseCase implements ManageTransferUseCase, QueryTransferUseCase {

    private static final Logger log = Logger.getLogger(TransferUseCase.class.getName());

    private static final String BRANCH = "la sucursal";
    private static final String PRODUCT = "el producto";
    private static final String TRANSFER = "la transferencia";
    private static final String CARRIER = "el transportista";
    private static final String ROUTE = "la ruta logística";

    private final TransferRepositoryPort transferRepository;
    private final TransferIssueRepositoryPort transferIssueRepository;
    private final TransferStatusHistoryRepositoryPort statusHistoryRepository;
    private final BranchRepositoryPort branchRepository;
    private final ProductRepositoryPort productRepository;
    private final CarrierRepositoryPort carrierRepository;
    private final LogisticsRouteRepositoryPort routeRepository;
    private final InventoryRepositoryPort inventoryRepository;
    private final InventoryMovementPoster poster;

    public TransferUseCase(TransferRepositoryPort transferRepository,
                           TransferIssueRepositoryPort transferIssueRepository,
                           TransferStatusHistoryRepositoryPort statusHistoryRepository,
                           BranchRepositoryPort branchRepository,
                           ProductRepositoryPort productRepository,
                           CarrierRepositoryPort carrierRepository,
                           LogisticsRouteRepositoryPort routeRepository,
                           InventoryRepositoryPort inventoryRepository,
                           InventoryMovementPoster poster) {
        this.transferRepository = transferRepository;
        this.transferIssueRepository = transferIssueRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.branchRepository = branchRepository;
        this.productRepository = productRepository;
        this.carrierRepository = carrierRepository;
        this.routeRepository = routeRepository;
        this.inventoryRepository = inventoryRepository;
        this.poster = poster;
    }

    @Override
    public Transfer createTransfer(CreateTransferCommand command) {
        if (!branchRepository.existsById(command.originBranchId())) {
            throw new ResourceNotFoundException(BRANCH, command.originBranchId());
        }
        if (!branchRepository.existsById(command.destinationBranchId())) {
            throw new ResourceNotFoundException(BRANCH, command.destinationBranchId());
        }
        if (transferRepository.existsByTransferNumber(command.transferNumber())) {
            throw new DuplicateResourceException(TRANSFER, "número", command.transferNumber());
        }

        List<TransferItem> items = command.items().stream().map(this::toItem).toList();

        Transfer transfer = Transfer.create(command.originBranchId(), command.destinationBranchId(),
                command.requestedBy(), command.transferNumber(), TransferPriority.fromString(command.priority()),
                command.notes(), items);

        Transfer saved = transferRepository.save(transfer);
        recordHistory(saved, command.requestedBy(), null);
        log.info(() -> "Transferencia solicitada: id=%s, número=%s, líneas=%d"
                .formatted(saved.getId(), saved.getTransferNumber(), items.size()));
        return saved;
    }

    @Override
    public Transfer approveTransfer(ApproveTransferCommand command) {
        Transfer transfer = loadTransfer(command.transferId());
        Map<UUID, Quantity> approvedQuantities = toQuantityMap(command.approvedQuantities().stream()
                .collect(Collectors.toMap(
                        ApproveTransferCommand.ItemQuantity::itemId, ApproveTransferCommand.ItemQuantity::quantity)));

        transfer.approve(command.approvedBy(), approvedQuantities);
        Transfer saved = transferRepository.save(transfer);
        recordHistory(saved, command.approvedBy(), null);
        return saved;
    }

    @Override
    public Transfer assignLogistics(AssignTransferLogisticsCommand command) {
        Transfer transfer = loadTransfer(command.transferId());

        if (!carrierRepository.existsById(command.carrierId())) {
            throw new ResourceNotFoundException(CARRIER, command.carrierId());
        }
        LogisticsRoute route = routeRepository.findById(command.routeId())
                .orElseThrow(() -> new ResourceNotFoundException(ROUTE, command.routeId()));
        if (!route.connects(transfer.getOriginBranchId(), transfer.getDestinationBranchId())) {
            throw new DomainValidationException("routeId",
                    "La ruta no conecta el origen y el destino de esta transferencia.");
        }

        transfer.assignLogistics(command.carrierId(), command.routeId(), command.estimatedArrivalAt());
        Transfer saved = transferRepository.save(transfer);
        log.info(() -> "Logística asignada a transferencia: id=%s, transportista=%s, ruta=%s"
                .formatted(saved.getId(), saved.getCarrierId(), saved.getRouteId()));
        return saved;
    }

    @Override
    public Transfer startPreparation(UUID transferId, UUID userId) {
        Transfer transfer = loadTransfer(transferId);
        transfer.startPreparation();
        Transfer saved = transferRepository.save(transfer);
        recordHistory(saved, userId, null);
        return saved;
    }

    @Override
    public Transfer dispatchTransfer(DispatchTransferCommand command) {
        Transfer transfer = loadTransfer(command.transferId());
        Map<UUID, Quantity> shippedQuantities = toQuantityMap(command.shippedQuantities().stream()
                .collect(Collectors.toMap(
                        DispatchTransferCommand.ItemQuantity::itemId, DispatchTransferCommand.ItemQuantity::quantity)));

        transfer.dispatch(shippedQuantities);
        Transfer saved = transferRepository.save(transfer);

        for (TransferItem item : saved.getItems()) {
            if (item.getShippedQuantity() == null || item.getShippedQuantity().isZero()) {
                continue;
            }
            postMovement(saved.getOriginBranchId(), item, item.getShippedQuantity(), InventoryMovementType.TRANSFER_OUT,
                    saved, command.userId());
        }

        recordHistory(saved, command.userId(), null);
        log.info(() -> "Transferencia despachada: id=%s, número=%s".formatted(saved.getId(), saved.getTransferNumber()));
        return saved;
    }

    @Override
    public Transfer receiveTransfer(ReceiveTransferCommand command) {
        Transfer transfer = loadTransfer(command.transferId());
        Map<UUID, Quantity> receivedQuantities = toQuantityMap(command.receivedQuantities().stream()
                .collect(Collectors.toMap(
                        ReceiveTransferCommand.ItemQuantity::itemId, ReceiveTransferCommand.ItemQuantity::quantity)));

        List<TransferItem> shortfallItems = transfer.receive(receivedQuantities);
        Transfer saved = transferRepository.save(transfer);

        for (TransferItem item : saved.getItems()) {
            if (item.getReceivedQuantity() == null || item.getReceivedQuantity().isZero()) {
                continue;
            }
            postMovement(saved.getDestinationBranchId(), item, item.getReceivedQuantity(),
                    InventoryMovementType.TRANSFER_IN, saved, command.userId());
        }

        for (TransferItem item : shortfallItems) {
            TransferIssue issue = TransferIssue.report(item.getId(), TransferIssueType.MISSING,
                    item.missingQuantity(),
                    "Faltante al recibir la transferencia %s: producto %s.".formatted(
                            saved.getTransferNumber(), item.getProductId()),
                    command.userId());
            transferIssueRepository.save(issue);
        }

        recordHistory(saved, command.userId(), null);
        log.info(() -> "Transferencia recibida: id=%s, número=%s, estado=%s, incidencias=%d"
                .formatted(saved.getId(), saved.getTransferNumber(), saved.getStatus(), shortfallItems.size()));
        return saved;
    }

    @Override
    public Transfer cancelTransfer(UUID transferId, UUID userId) {
        Transfer transfer = loadTransfer(transferId);
        transfer.cancel();
        Transfer saved = transferRepository.save(transfer);
        recordHistory(saved, userId, null);
        return saved;
    }

    @Override
    public Transfer getTransferById(UUID transferId) {
        return loadTransfer(transferId);
    }

    @Override
    public PageResult<Transfer> searchTransfers(TransferSearchCriteria criteria, PageQuery pageQuery) {
        return transferRepository.search(criteria, pageQuery);
    }

    private void postMovement(UUID branchId, TransferItem item, Quantity quantity,
                              InventoryMovementType type, Transfer transfer, UUID userId) {
        Product product = requireProduct(item.getProductId());

        BigDecimal unitCost = type == InventoryMovementType.TRANSFER_IN
                ? resolveOriginCost(transfer.getOriginBranchId(), item.getProductId())
                : null;

        poster.post(new PostInventoryMovementCommand(branchId, item.getProductId(), product.getSku(), type,
                quantity.value(), unitCost, "Transferencia %s".formatted(transfer.getTransferNumber()), userId,
                Instant.now(), null, null, transfer.getId(), null));
    }

    /**
     * El costo con el que se recibe una transferencia es el costo promedio ponderado que ya
     * tenía el saldo de origen: la transferencia no genera valor nuevo, solo lo mueve entre
     * sucursales (a diferencia de {@code PURCHASE_IN}, que sí trae un costo real de compra).
     */
    private BigDecimal resolveOriginCost(UUID originBranchId, UUID productId) {
        return inventoryRepository.findByBranchIdAndProductId(originBranchId, productId)
                .map(Inventory::getAverageCost)
                .map(Money::amount)
                .orElse(BigDecimal.ZERO);
    }

    private TransferItem toItem(CreateTransferCommand.Item item) {
        requireProduct(item.productId());

        return TransferItem.create(item.productId(), Quantity.of(item.quantity()));
    }

    private Map<UUID, Quantity> toQuantityMap(Map<UUID, BigDecimal> raw) {
        Map<UUID, Quantity> result = new HashMap<>();
        raw.forEach((itemId, quantity) -> result.put(itemId, Quantity.of(quantity)));
        return result;
    }

    private void recordHistory(Transfer transfer, UUID changedBy, String notes) {
        statusHistoryRepository.save(TransferStatusHistory.record(transfer.getId(), transfer.getStatus(),
                changedBy, notes));
    }

    private Transfer loadTransfer(UUID transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException(TRANSFER, transferId));
    }

    private Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(PRODUCT, productId));
    }
}
