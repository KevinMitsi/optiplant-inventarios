package io.github.KevinMitsi.inventories.application.service;

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
import io.github.KevinMitsi.inventories.domain.model.Branch;
import io.github.KevinMitsi.inventories.domain.model.PageQuery;
import io.github.KevinMitsi.inventories.domain.model.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Orquestación de los casos de uso de sucursal.
 *
 * <p>Las reglas propias del agregado viven en {@link Branch}. Aquí solo lo que este no puede
 * saber por sí solo: si la organización existe, si el código ya está en uso, y los límites
 * de la transacción.
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class BranchService implements CreateBranchUseCase,
                                      UpdateBranchUseCase,
                                      ChangeBranchStatusUseCase,
                                      QueryBranchUseCase {

    private static final Logger log = LoggerFactory.getLogger(BranchService.class);

    private static final String BRANCH = "la sucursal";
    private static final String ORGANIZATION = "la organización";

    private final BranchRepositoryPort branchRepository;
    private final OrganizationRepositoryPort organizationRepository;

    public BranchService(BranchRepositoryPort branchRepository,
                         OrganizationRepositoryPort organizationRepository) {
        this.branchRepository = branchRepository;
        this.organizationRepository = organizationRepository;
    }

    // ----------------------------------------------------------------------------------
    // Escritura
    // ----------------------------------------------------------------------------------

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
        log.info("Sucursal creada: id={}, código={}, organización={}",
                saved.getId(), saved.getCode(), saved.getOrganizationId());
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
        log.info("Sucursal actualizada: id={}", saved.getId());
        return saved;
    }

    @Override
    public Branch deactivateBranch(UUID branchId) {
        Branch branch = loadBranch(branchId);
        branch.deactivate();

        Branch saved = branchRepository.save(branch);
        log.info("Sucursal desactivada: id={}, código={}", saved.getId(), saved.getCode());
        return saved;
    }

    @Override
    public Branch activateBranch(UUID branchId) {
        Branch branch = loadBranch(branchId);
        branch.activate();

        Branch saved = branchRepository.save(branch);
        log.info("Sucursal activada: id={}, código={}", saved.getId(), saved.getCode());
        return saved;
    }

    // ----------------------------------------------------------------------------------
    // Lectura
    // ----------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Branch getBranchById(UUID branchId) {
        return loadBranch(branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public Branch getBranchByCode(UUID organizationId, String code) {
        String normalizedCode = normalizeCode(code);
        return branchRepository.findByOrganizationIdAndCode(organizationId, normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException(BRANCH, "código", normalizedCode));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Branch> searchBranches(BranchSearchCriteria criteria, PageQuery pageQuery) {
        return branchRepository.search(criteria, pageQuery);
    }

    // ----------------------------------------------------------------------------------
    // Apoyo
    // ----------------------------------------------------------------------------------

    private Branch loadBranch(UUID branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException(BRANCH, branchId));
    }

    /**
     * Debe normalizar igual que {@link Branch}: si no, un alta duplicada pasaría la
     * comprobación previa para morir después contra el índice único con un error opaco.
     */
    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
