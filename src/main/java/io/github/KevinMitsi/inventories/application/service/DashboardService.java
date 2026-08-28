package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.QueryDashboardUseCase;
import io.github.KevinMitsi.inventories.domain.model.BranchComparison;
import io.github.KevinMitsi.inventories.domain.model.ProductRotation;
import io.github.KevinMitsi.inventories.domain.model.SalesSummary;
import io.github.KevinMitsi.inventories.domain.usecase.DashboardUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Primary
@Service
@Transactional(rollbackFor = Exception.class)
public class DashboardService implements QueryDashboardUseCase {

    private final DashboardUseCase useCase;

    public DashboardService(DashboardUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public List<SalesSummary> getSalesSummary(UUID organizationId, UUID branchId, Instant from, Instant to) {
        return useCase.getSalesSummary(organizationId, branchId, from, to);
    }

    @Override
    public List<ProductRotation> getProductRotation(UUID organizationId, UUID branchId, Instant from, Instant to) {
        return useCase.getProductRotation(organizationId, branchId, from, to);
    }

    @Override
    public List<BranchComparison> getBranchComparison(UUID organizationId) {
        return useCase.getBranchComparison(organizationId);
    }
}
