package io.github.KevinMitsi.inventories.domain.usecase;

import io.github.KevinMitsi.inventories.application.exception.DuplicateResourceException;
import io.github.KevinMitsi.inventories.application.exception.ResourceNotFoundException;
import io.github.KevinMitsi.inventories.application.port.in.ChangeBranchStatusUseCase;
import io.github.KevinMitsi.inventories.application.port.in.CreateBranchUseCase;
import io.github.KevinMitsi.inventories.application.port.in.QueryBranchUseCase;
import io.github.KevinMitsi.inventories.application.port.in.UpdateBranchUseCase;
import io.github.KevinMitsi.inventories.application.port.in.command.CreateBranchCommand;
import io.github.KevinMitsi.inventories.application.port.in.command.UpdateBranchCommand;
import io.github.KevinMitsi.inventories.application.port.in.query.BranchSearchCriteria;
import io.github.KevinMitsi.inventories.application.port.out.BranchRepositoryPort;
import io.github.KevinMitsi.inventories.application.port.out.OrganizationRepositoryPort;
import io.github.KevinMitsi.inventories.domain.annotation.AuditedUseCase;
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;

import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

@AuditedUseCase
public class BranchUseCase implements CreateBranchUseCase,
                                      UpdateBranchUseCase,
                                      ChangeBranchStatusUseCase,
                                      QueryBranchUseCase {

    private static final Logger log = Logger.getLogger(BranchUseCase.class.getName());

    private static final String BRANCH = "la sucursal";
    private static final String ORGANIZATION = "la organización";

    private final BranchRepositoryPort branchRepository;
    private final OrganizationRepositoryPort organizationRepository;

    public BranchUseCase(BranchRepositoryPort branchRepository,
                         OrganizationRepositoryPort organizationRepository) {
        this.branchRepository = branchRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public Branch createBranch(CreateBranchCommand command) {
        if (!organizationRepository.existsById(command.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, command.organizationId());
        }

        String normalizedCode = normalizeCode(command.code());
        if (branchRepository.existsByOrganizationIdAndCode(command.organizationId(), normalizedCode)) {
            throw new DuplicateResourceException(BRANCH, "código", normalizedCode);
        }

        Branch branch = Branch.create(
                command.organizationId(),
                normalizedCode,
                command.name(),
                command.addressLine(),
                command.city(),
                command.countryCode(),
                command.phone());

        Branch saved = branchRepository.save(branch);
        log.info(() -> "Sucursal creada: id=%s, código=%s, organización=%s"
                .formatted(saved.getId(), saved.getCode(), saved.getOrganizationId()));
        return saved;
    }

    @Override
    public Branch updateBranch(UpdateBranchCommand command) {
        Branch branch = loadBranch(command.branchId());

        branch.updateDetails(
                command.name(),
                command.addressLine(),
                command.city(),
                command.countryCode(),
                command.phone());

        Branch saved = branchRepository.save(branch);
        log.info(() -> "Sucursal actualizada: id=%s".formatted(saved.getId()));
        return saved;
    }

    @Override
    public Branch deactivateBranch(UUID branchId) {
        Branch branch = loadBranch(branchId);
        branch.deactivate();

        Branch saved = branchRepository.save(branch);
        log.info(() -> "Sucursal desactivada: id=%s, código=%s".formatted(saved.getId(), saved.getCode()));
        return saved;
    }

    @Override
    public Branch activateBranch(UUID branchId) {
        Branch branch = loadBranch(branchId);
        branch.activate();

        Branch saved = branchRepository.save(branch);
        log.info(() -> "Sucursal activada: id=%s, código=%s".formatted(saved.getId(), saved.getCode()));
        return saved;
    }

    @Override
    public Branch getBranchById(UUID branchId) {
        return loadBranch(branchId);
    }

    @Override
    public Branch getBranchByCode(UUID organizationId, String code) {
        String normalizedCode = normalizeCode(code);
        return branchRepository.findByOrganizationIdAndCode(organizationId, normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException(BRANCH, "código", normalizedCode));
    }

    @Override
    public PageResult<Branch> searchBranches(BranchSearchCriteria criteria, PageQuery pageQuery) {
        return branchRepository.search(criteria, pageQuery);
    }

    private Branch loadBranch(UUID branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException(BRANCH, branchId));
    }

    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
