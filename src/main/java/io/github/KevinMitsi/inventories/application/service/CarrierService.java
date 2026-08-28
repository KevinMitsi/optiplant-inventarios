package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ManageCarrierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryCarrierUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateCarrierCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateCarrierCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.CarrierSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Carrier;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.usecase.CarrierUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Primary
@Service
@Transactional(rollbackFor = Exception.class)
public class CarrierService implements ManageCarrierUseCase, QueryCarrierUseCase {

    private final CarrierUseCase useCase;

    public CarrierService(CarrierUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Carrier createCarrier(CreateCarrierCommand command) {
        return useCase.createCarrier(command);
    }

    @Override
    public Carrier updateCarrier(UpdateCarrierCommand command) {
        return useCase.updateCarrier(command);
    }

    @Override
    public Carrier deactivateCarrier(UUID carrierId) {
        return useCase.deactivateCarrier(carrierId);
    }

    @Override
    public Carrier activateCarrier(UUID carrierId) {
        return useCase.activateCarrier(carrierId);
    }

    @Override
    public Carrier getCarrierById(UUID carrierId) {
        return useCase.getCarrierById(carrierId);
    }

    @Override
    public PageResult<Carrier> searchCarriers(CarrierSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchCarriers(criteria, pageQuery);
    }
}
