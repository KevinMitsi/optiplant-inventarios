package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ManageCarrierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryCarrierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateCarrierCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateCarrierCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.CarrierSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.CarrierRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.annotation.AuditedUseCase;
import io.github.KevinMitsi.inventories.domain.model.Carrier;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.UUID;
import java.util.logging.Logger;

@AuditedUseCase
public class CarrierUseCase implements ManageCarrierUseCase, QueryCarrierUseCase {

    private static final Logger log = Logger.getLogger(CarrierUseCase.class.getName());

    private static final String CARRIER = "el transportista";
    private static final String ORGANIZATION = "la organización";

    private final CarrierRepositoryPort carrierRepository;
    private final OrganizationRepositoryPort organizationRepository;

    public CarrierUseCase(CarrierRepositoryPort carrierRepository, OrganizationRepositoryPort organizationRepository) {
        this.carrierRepository = carrierRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public Carrier createCarrier(CreateCarrierCommand command) {
        if (!organizationRepository.existsById(command.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, command.organizationId());
        }

        Carrier carrier = Carrier.create(command.organizationId(), command.code(), command.name(),
                command.phone(), command.email());

        if (carrierRepository.existsByOrganizationIdAndCode(command.organizationId(), carrier.getCode())) {
            throw new DuplicateResourceException(CARRIER, "código", carrier.getCode());
        }

        Carrier saved = carrierRepository.save(carrier);
        log.info(() -> "Transportista creado: id=%s, código=%s".formatted(saved.getId(), saved.getCode()));
        return saved;
    }

    @Override
    public Carrier updateCarrier(UpdateCarrierCommand command) {
        Carrier carrier = loadCarrier(command.carrierId());
        carrier.updateDetails(command.name(), command.phone(), command.email());
        return carrierRepository.save(carrier);
    }

    @Override
    public Carrier deactivateCarrier(UUID carrierId) {
        Carrier carrier = loadCarrier(carrierId);
        carrier.deactivate();
        return carrierRepository.save(carrier);
    }

    @Override
    public Carrier activateCarrier(UUID carrierId) {
        Carrier carrier = loadCarrier(carrierId);
        carrier.activate();
        return carrierRepository.save(carrier);
    }

    @Override
    public Carrier getCarrierById(UUID carrierId) {
        return loadCarrier(carrierId);
    }

    @Override
    public PageResult<Carrier> searchCarriers(CarrierSearchCriteria criteria, PageQuery pageQuery) {
        return carrierRepository.search(criteria, pageQuery);
    }

    private Carrier loadCarrier(UUID carrierId) {
        return carrierRepository.findById(carrierId)
                .orElseThrow(() -> new ResourceNotFoundException(CARRIER, carrierId));
    }
}
