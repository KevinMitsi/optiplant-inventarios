package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageSupplierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QuerySupplierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateSupplierCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateSupplierCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.SupplierSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Supplier;
import io.github.KevinMitsi.inventories.domain.usecase.SupplierUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class SupplierService implements ManageSupplierUseCase, QuerySupplierUseCase {

    private final SupplierUseCase useCase;

    public SupplierService(SupplierUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Supplier createSupplier(CreateSupplierCommand command) {
        return useCase.createSupplier(command);
    }

    @Override
    public Supplier updateSupplier(UpdateSupplierCommand command) {
        return useCase.updateSupplier(command);
    }

    @Override
    public Supplier deactivateSupplier(UUID supplierId) {
        return useCase.deactivateSupplier(supplierId);
    }

    @Override
    public Supplier activateSupplier(UUID supplierId) {
        return useCase.activateSupplier(supplierId);
    }

    @Override
    public Supplier getSupplierById(UUID supplierId) {
        return useCase.getSupplierById(supplierId);
    }

    @Override
    public PageResult<Supplier> searchSuppliers(SupplierSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchSuppliers(criteria, pageQuery);
    }
}
