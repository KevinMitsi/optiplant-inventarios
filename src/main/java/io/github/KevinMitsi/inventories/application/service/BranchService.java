package io.github.KevinMitsi.inventories.application.service;

import io.github.KevinMitsi.inventories.application.port.in.ChangeBranchStatusUseCase;
import io.github.KevinMitsi.inventories.application.port.in.CreateBranchUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryBranchUseCase;
import io.github.KevinMitsi.inventories.application.port.in.UpdateBranchUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateBranchCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateBranchCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.BranchSearchCriteria;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import io.github.KevinMitsi.inventories.domain.usecase.BranchUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(rollbackFor = Exception.class)
public class BranchService implements CreateBranchUseCase, UpdateBranchUseCase, ChangeBranchStatusUseCase, QueryBranchUseCase {

    private final BranchUseCase useCase;

    public BranchService(BranchUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public Branch createBranch(CreateBranchCommand command) {
        return useCase.createBranch(command);
    }

    @Override
    public Branch updateBranch(UpdateBranchCommand command) {
        return useCase.updateBranch(command);
    }

    @Override
    public Branch deactivateBranch(UUID branchId) {
        return useCase.deactivateBranch(branchId);
    }

    @Override
    public Branch activateBranch(UUID branchId) {
        return useCase.activateBranch(branchId);
    }

    @Override
    public Branch getBranchById(UUID branchId) {
        return useCase.getBranchById(branchId);
    }

    @Override
    public Branch getBranchByCode(UUID organizationId, String code) {
        return useCase.getBranchByCode(organizationId, code);
    }

    @Override
    public PageResult<Branch> searchBranches(BranchSearchCriteria criteria, PageQuery pageQuery) {
        return useCase.searchBranches(criteria, pageQuery);
    }
}
