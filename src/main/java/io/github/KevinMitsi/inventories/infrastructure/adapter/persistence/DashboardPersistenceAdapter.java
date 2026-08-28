package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence;

import io.github.KevinMitsi.inventories.application.port.out.DashboardRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.BranchComparison;
import io.github.KevinMitsi.inventories.domain.model.ProductRotation;
import io.github.KevinMitsi.inventories.domain.model.SalesSummary;
import io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.repository.DashboardJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DashboardPersistenceAdapter implements DashboardRepositoryPort {

    private final DashboardJpaRepository repository;

    @Override
    public List<SalesSummary> getSalesSummary(UUID organizationId, UUID branchId, Instant from, Instant to) {
        return repository.getSalesSummary(organizationId, branchId, from, to).stream()
                .map(row -> new SalesSummary(row.getBranchId(), row.getBranchName(), row.getYear(), row.getMonth(),
                        row.getSaleCount(), row.getTotalAmount()))
                .toList();
    }

    @Override
    public List<ProductRotation> getProductRotation(UUID organizationId, UUID branchId, Instant from, Instant to) {
        return repository.getProductRotation(organizationId, branchId, from, to).stream()
                .map(row -> new ProductRotation(row.getProductId(), row.getProductName(), row.getQuantitySold(),
                        row.getSaleCount()))
                .toList();
    }

    @Override
    public List<BranchComparison> getBranchComparison(UUID organizationId) {
        return repository.getBranchComparison(organizationId).stream()
                .map(row -> new BranchComparison(row.getBranchId(), row.getBranchName(), row.getSaleCount30d(),
                        row.getTotalSalesAmount30d(), row.getInventoryValue(), row.getLowStockCount()))
                .toList();
    }
}
