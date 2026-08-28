package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageSupplierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QuerySupplierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateSupplierCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateSupplierCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.SupplierSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.SupplierRepositoryPort;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.model.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Gestión de proveedores (RF-17): el mínimo que exige el módulo de compras. */
@Service
@Transactional(rollbackFor = Exception.class)
public class SupplierService implements ManageSupplierUseCase, QuerySupplierUseCase {

    private static final Logger log = LoggerFactory.getLogger(SupplierService.class);

    private static final String SUPPLIER = "el proveedor";
    private static final String ORGANIZATION = "la organización";

    private final SupplierRepositoryPort supplierRepository;
    private final OrganizationRepositoryPort organizationRepository;

    public SupplierService(SupplierRepositoryPort supplierRepository,
                           OrganizationRepositoryPort organizationRepository) {
        this.supplierRepository = supplierRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public Supplier createSupplier(CreateSupplierCommand command) {
        if (!organizationRepository.existsById(command.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, command.organizationId());
        }

        Supplier supplier = Supplier.create(command.organizationId(), command.code(), command.name(),
                command.taxId(), command.email(), command.phone());

        if (supplierRepository.existsByOrganizationIdAndCode(command.organizationId(), supplier.getCode())) {
            throw new DuplicateResourceException(SUPPLIER, "código", supplier.getCode());
        }

        Supplier saved = supplierRepository.save(supplier);
        log.info("Proveedor creado: id={}, código={}", saved.getId(), saved.getCode());
        return saved;
    }

    @Override
    public Supplier updateSupplier(UpdateSupplierCommand command) {
        Supplier supplier = loadSupplier(command.supplierId());
        supplier.updateDetails(command.name(), command.taxId(), command.email(), command.phone());
        return supplierRepository.save(supplier);
    }

    @Override
    public Supplier deactivateSupplier(UUID supplierId) {
        Supplier supplier = loadSupplier(supplierId);
        supplier.deactivate();
        return supplierRepository.save(supplier);
    }

    @Override
    public Supplier activateSupplier(UUID supplierId) {
        Supplier supplier = loadSupplier(supplierId);
        supplier.activate();
        return supplierRepository.save(supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public Supplier getSupplierById(UUID supplierId) {
        return loadSupplier(supplierId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Supplier> searchSuppliers(SupplierSearchCriteria criteria, PageQuery pageQuery) {
        return supplierRepository.search(criteria, pageQuery);
    }

    private Supplier loadSupplier(UUID supplierId) {
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException(SUPPLIER, supplierId));
    }
}
