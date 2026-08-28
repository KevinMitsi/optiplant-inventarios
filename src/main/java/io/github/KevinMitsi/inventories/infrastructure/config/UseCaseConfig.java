package io.github.KevinMitsi.inventories.infrastructure.config;

import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.CarrierRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.CategoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryAdjustmentRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryAlertRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryMovementRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.InventoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.LogisticsRouteRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PasswordHasherPort;
import io.github.KevinMitsi.inventories.application.port.out.PriceListRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductPriceRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.ProductRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.PurchaseOrderRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.RoleRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.SaleRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.SupplierRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TokenProviderPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferIssueRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.TransferStatusHistoryRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.UnitOfMeasureRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.UserRepositoryPort;
import io.github.KevinMitsi.inventories.domain.usecase.AuthenticationUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.BranchUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.CarrierUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.CategoryUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.InventoryAdjustmentUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.InventoryAlertUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.InventoryMovementPoster;
import io.github.KevinMitsi.inventories.domain.usecase.InventoryUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.LogisticsRouteUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.PriceListUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.ProductUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.PurchaseOrderUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.SaleUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.SupplierUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.TransferIssueUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.TransferUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.UnitOfMeasureUseCase;
import io.github.KevinMitsi.inventories.domain.usecase.UserUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Único punto donde infraestructura conoce las clases concretas de {@code domain.usecase}.
 * Cada una se instancia con {@code new} (constructor plano, sin anotaciones). Los puertos
 * {@code in} se exponen a través de {@code application.service}, que envuelve cada
 * caso de uso en una transacción.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public InventoryMovementPoster inventoryMovementPoster(InventoryRepositoryPort inventoryRepositoryPort,
                                                             InventoryMovementRepositoryPort inventoryMovementRepositoryPort,
                                                             InventoryAlertRepositoryPort inventoryAlertRepositoryPort) {
        return new InventoryMovementPoster(inventoryRepositoryPort, inventoryMovementRepositoryPort, inventoryAlertRepositoryPort);
    }

    @Bean
    public AuthenticationUseCase authenticationUseCase(UserRepositoryPort userRepositoryPort,
                                                         PasswordHasherPort passwordHasherPort,
                                                         TokenProviderPort tokenProviderPort) {
        return new AuthenticationUseCase(userRepositoryPort, passwordHasherPort, tokenProviderPort);
    }

    @Bean
    public BranchUseCase branchUseCase(BranchRepositoryPort branchRepositoryPort,
                                        OrganizationRepositoryPort organizationRepositoryPort) {
        return new BranchUseCase(branchRepositoryPort, organizationRepositoryPort);
    }

    @Bean
    public CategoryUseCase categoryUseCase(CategoryRepositoryPort categoryRepositoryPort,
                                            OrganizationRepositoryPort organizationRepositoryPort) {
        return new CategoryUseCase(categoryRepositoryPort, organizationRepositoryPort);
    }

    @Bean
    public UnitOfMeasureUseCase unitOfMeasureUseCase(UnitOfMeasureRepositoryPort unitOfMeasureRepositoryPort) {
        return new UnitOfMeasureUseCase(unitOfMeasureRepositoryPort);
    }

    @Bean
    public ProductUseCase productUseCase(ProductRepositoryPort productRepositoryPort,
                                          CategoryRepositoryPort categoryRepositoryPort,
                                          UnitOfMeasureRepositoryPort unitOfMeasureRepositoryPort,
                                          OrganizationRepositoryPort organizationRepositoryPort) {
        return new ProductUseCase(
                productRepositoryPort, categoryRepositoryPort, unitOfMeasureRepositoryPort, organizationRepositoryPort);
    }

    @Bean
    public SupplierUseCase supplierUseCase(SupplierRepositoryPort supplierRepositoryPort,
                                            OrganizationRepositoryPort organizationRepositoryPort) {
        return new SupplierUseCase(supplierRepositoryPort, organizationRepositoryPort);
    }

    @Bean
    public PriceListUseCase priceListUseCase(PriceListRepositoryPort priceListRepositoryPort,
                                              ProductPriceRepositoryPort productPriceRepositoryPort,
                                              OrganizationRepositoryPort organizationRepositoryPort,
                                              ProductRepositoryPort productRepositoryPort) {
        return new PriceListUseCase(
                priceListRepositoryPort, productPriceRepositoryPort, organizationRepositoryPort, productRepositoryPort);
    }

    @Bean
    public InventoryUseCase inventoryUseCase(InventoryRepositoryPort inventoryRepositoryPort,
                                              InventoryMovementRepositoryPort inventoryMovementRepositoryPort,
                                              BranchRepositoryPort branchRepositoryPort,
                                              ProductRepositoryPort productRepositoryPort,
                                              InventoryMovementPoster inventoryMovementPoster) {
        return new InventoryUseCase(inventoryRepositoryPort, inventoryMovementRepositoryPort,
                branchRepositoryPort, productRepositoryPort, inventoryMovementPoster);
    }

    @Bean
    public InventoryAdjustmentUseCase inventoryAdjustmentUseCase(
            InventoryAdjustmentRepositoryPort inventoryAdjustmentRepositoryPort,
            BranchRepositoryPort branchRepositoryPort,
            ProductRepositoryPort productRepositoryPort,
            InventoryMovementPoster inventoryMovementPoster) {
        return new InventoryAdjustmentUseCase(
                inventoryAdjustmentRepositoryPort, branchRepositoryPort, productRepositoryPort, inventoryMovementPoster);
    }

    @Bean
    public InventoryAlertUseCase inventoryAlertUseCase(InventoryAlertRepositoryPort inventoryAlertRepositoryPort) {
        return new InventoryAlertUseCase(inventoryAlertRepositoryPort);
    }

    @Bean
    public PurchaseOrderUseCase purchaseOrderUseCase(PurchaseOrderRepositoryPort purchaseOrderRepositoryPort,
                                                       BranchRepositoryPort branchRepositoryPort,
                                                       SupplierRepositoryPort supplierRepositoryPort,
                                                       ProductRepositoryPort productRepositoryPort,
                                                       InventoryMovementPoster inventoryMovementPoster) {
        return new PurchaseOrderUseCase(purchaseOrderRepositoryPort, branchRepositoryPort,
                supplierRepositoryPort, productRepositoryPort, inventoryMovementPoster);
    }

    @Bean
    public SaleUseCase saleUseCase(SaleRepositoryPort saleRepositoryPort,
                                    BranchRepositoryPort branchRepositoryPort,
                                    ProductRepositoryPort productRepositoryPort,
                                    PriceListRepositoryPort priceListRepositoryPort,
                                    ProductPriceRepositoryPort productPriceRepositoryPort,
                                    InventoryMovementPoster inventoryMovementPoster) {
        return new SaleUseCase(saleRepositoryPort, branchRepositoryPort, productRepositoryPort,
                priceListRepositoryPort, productPriceRepositoryPort, inventoryMovementPoster);
    }

    @Bean
    public TransferUseCase transferUseCase(TransferRepositoryPort transferRepositoryPort,
                                            TransferIssueRepositoryPort transferIssueRepositoryPort,
                                            TransferStatusHistoryRepositoryPort transferStatusHistoryRepositoryPort,
                                            BranchRepositoryPort branchRepositoryPort,
                                            ProductRepositoryPort productRepositoryPort,
                                            CarrierRepositoryPort carrierRepositoryPort,
                                            LogisticsRouteRepositoryPort logisticsRouteRepositoryPort,
                                            InventoryMovementPoster inventoryMovementPoster) {
        return new TransferUseCase(transferRepositoryPort, transferIssueRepositoryPort,
                transferStatusHistoryRepositoryPort, branchRepositoryPort, productRepositoryPort,
                carrierRepositoryPort, logisticsRouteRepositoryPort, inventoryMovementPoster);
    }

    @Bean
    public CarrierUseCase carrierUseCase(CarrierRepositoryPort carrierRepositoryPort,
                                          OrganizationRepositoryPort organizationRepositoryPort) {
        return new CarrierUseCase(carrierRepositoryPort, organizationRepositoryPort);
    }

    @Bean
    public LogisticsRouteUseCase logisticsRouteUseCase(LogisticsRouteRepositoryPort logisticsRouteRepositoryPort,
                                                         OrganizationRepositoryPort organizationRepositoryPort,
                                                         BranchRepositoryPort branchRepositoryPort) {
        return new LogisticsRouteUseCase(logisticsRouteRepositoryPort, organizationRepositoryPort, branchRepositoryPort);
    }

    @Bean
    public TransferIssueUseCase transferIssueUseCase(TransferIssueRepositoryPort transferIssueRepositoryPort,
                                                       TransferRepositoryPort transferRepositoryPort) {
        return new TransferIssueUseCase(transferIssueRepositoryPort, transferRepositoryPort);
    }

    @Bean
    public UserUseCase userUseCase(UserRepositoryPort userRepositoryPort,
                                    RoleRepositoryPort roleRepositoryPort,
                                    BranchRepositoryPort branchRepositoryPort,
                                    OrganizationRepositoryPort organizationRepositoryPort,
                                    PasswordHasherPort passwordHasherPort) {
        return new UserUseCase(
                userRepositoryPort, roleRepositoryPort, branchRepositoryPort, organizationRepositoryPort, passwordHasherPort);
    }
}
