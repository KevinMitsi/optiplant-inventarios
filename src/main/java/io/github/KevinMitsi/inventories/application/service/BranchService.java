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
 * <p>Implementa los cuatro puertos de entrada en una sola clase, aunque cada uno se declare
 * por separado. La segregación existe para quien <em>consume</em> —un controlador depende
 * solo del caso de uso que invoca, y un doble de prueba implementa un único método—, no
 * para forzar cuatro implementaciones que compartirían las mismas dependencias y el mismo
 * repositorio.
 *
 * <p>El servicio no contiene reglas de negocio propias de la sucursal: validar el código,
 * normalizar el nombre o decidir si puede operar es responsabilidad de {@link Branch}. Aquí
 * vive lo que el agregado no puede saber por sí solo: si la organización existe, si el
 * código ya lo usa otra sucursal, y el arranque y cierre de la transacción.
 *
 * <p>Sobre las anotaciones de Spring en la capa de aplicación: {@code @Service} y
 * {@code @Transactional} son metadatos de cableado y demarcación, no lógica. El código no
 * llama a ninguna API de Spring y la clase se instancia con {@code new} en las pruebas
 * unitarias. Sacarlas a una configuración externa daría pureza nominal a cambio de
 * dispersar la definición de los límites transaccionales lejos del método que los necesita,
 * que es justo donde deben leerse.
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

    /**
     * Inyección por constructor y campos finales.
     *
     * <p>Deja explícitas las dependencias en la firma, impide construir el servicio a medio
     * inicializar y permite instanciarlo con dobles en una prueba unitaria sin contenedor.
     */
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
        // La clave foránea de la base rechazaría una organización inexistente, pero con un
        // error opaco. Comprobarlo aquí permite responder qué falta exactamente.
        if (!organizationRepository.existsById(command.organizationId())) {
            throw new ResourceNotFoundException(ORGANIZATION, command.organizationId());
        }

        // El código se normaliza igual que en el constructor de Branch para que la
        // comprobación de duplicados y la posterior inserción comparen el mismo valor.
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

        // El agregado valida y aplica el cambio. El servicio no reimplementa esas reglas.
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

    /**
     * Las lecturas van en transacción de solo lectura.
     *
     * <p>Hibernate omite la comprobación de cambios al cerrar y no mantiene copia de
     * respaldo de las entidades cargadas, lo que reduce memoria y trabajo por consulta.
     * Además impide que una operación de lectura escriba por accidente (RNF-07).
     */
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
     * Normaliza el código igual que hace {@link Branch}.
     *
     * <p>Sin esto, buscar {@code "bog-01"} no encontraría la sucursal guardada como
     * {@code "BOG-01"} y el alta duplicada pasaría la comprobación previa para morir
     * después contra el índice único.
     */
    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
