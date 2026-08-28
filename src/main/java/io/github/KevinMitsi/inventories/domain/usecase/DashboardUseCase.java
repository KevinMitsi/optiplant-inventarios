package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.QueryDashboardUseCase;
import io.github.KevinMitsi.inventories.application.port.out.DashboardRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.BranchComparison;
import io.github.KevinMitsi.inventories.domain.model.ProductRotation;
import io.github.KevinMitsi.inventories.domain.model.SalesSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DashboardUseCase implements QueryDashboardUseCase {

    private static final String ORGANIZATION = "la organización";

    private final DashboardRepositoryPort dashboardRepository;
    private final OrganizationRepositoryPort organizationRepository;

    public DashboardUseCase(DashboardRepositoryPort dashboardRepository,
                             OrganizationRepositoryPort organizationRepository) {
        this.dashboardRepository = dashboardRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public List<SalesSummary> getSalesSummary(UUID organizationId, UUID branchId, Instant from, Instant to) {
        requireOrganization(organizationId);
        return dashboardRepository.getSalesSummary(organizationId, branchId, from, to);
    }

    @Override
    public List<ProductRotation> getProductRotation(UUID organizationId, UUID branchId, Instant from, Instant to) {
        requireOrganization(organizationId);
        return dashboardRepository.getProductRotation(organizationId, branchId, from, to);
    }

    @Override
    public List<BranchComparison> getBranchComparison(UUID organizationId) {
        requireOrganization(organizationId);
        return dashboardRepository.getBranchComparison(organizationId);
    }

    private void requireOrganization(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ResourceNotFoundException(ORGANIZATION, organizationId);
        }
    }
}
