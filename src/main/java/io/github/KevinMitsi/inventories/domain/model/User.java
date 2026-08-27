package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Usuario del sistema.
 *
 * <p>Además de ser una ficha de acceso, es el sujeto de la trazabilidad: todo movimiento de
 * inventario registra quién lo provocó (RN-11), así que sin usuario no hay histórico
 * auditable. Y es también el portador del ámbito de autorización, porque el par
 * (rol, sucursal) determina sobre qué puede operar (RN-12, RN-13).
 *
 * <p>La contraseña se guarda exclusivamente como hash. El dominio la trata como un dato
 * opaco: no la interpreta, no la compara y no sabe con qué algoritmo se generó. Comparar y
 * generar hashes es trabajo de un puerto de salida, lo que mantiene BCrypt fuera del
 * dominio y permite cambiarlo sin tocar una regla de negocio.
 */
public final class User {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int EMAIL_MAX_LENGTH = 254;
    private static final int PASSWORD_HASH_MAX_LENGTH = 255;

    private final UUID id;
    private final UUID organizationId;
    private final Instant createdAt;

    private UUID branchId;
    private Role role;
    private String firstName;
    private String lastName;
    private String email;
    private String passwordHash;
    private boolean active;
    private Instant lastLoginAt;
    private Instant updatedAt;

    private User(UUID id,
                 UUID organizationId,
                 UUID branchId,
                 Role role,
                 String firstName,
                 String lastName,
                 String email,
                 String passwordHash,
                 boolean active,
                 Instant lastLoginAt,
                 Instant createdAt,
                 Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador del usuario no puede ser nulo.");
        this.organizationId = Objects.requireNonNull(organizationId,
                "El usuario debe pertenecer a una organización.");
        this.role = Objects.requireNonNull(role, "El usuario debe tener un rol asignado.");
        this.branchId = requireBranchConsistentWithRole(branchId, role);
        this.firstName = requireName(firstName, "firstName", "El nombre es obligatorio.");
        this.lastName = requireName(lastName, "lastName", "El apellido es obligatorio.");
        this.email = requireEmail(email);
        this.passwordHash = requirePasswordHash(passwordHash);
        this.active = active;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    /**
     * Da de alta un usuario.
     *
     * @param passwordHash contraseña <b>ya cifrada</b>. El dominio nunca recibe la
     *                     contraseña en claro, de modo que no puede acabar en un log, en un
     *                     mensaje de excepción ni en una traza por descuido.
     */
    public static User create(UUID organizationId,
                              UUID branchId,
                              Role role,
                              String firstName,
                              String lastName,
                              String email,
                              String passwordHash) {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), organizationId, branchId, role,
                firstName, lastName, email, passwordHash, true, null, now, now);
    }

    /** Reconstruye un usuario ya persistido. Solo lo usa el adaptador de persistencia. */
    public static User reconstitute(UUID id,
                                    UUID organizationId,
                                    UUID branchId,
                                    Role role,
                                    String firstName,
                                    String lastName,
                                    String email,
                                    String passwordHash,
                                    boolean active,
                                    Instant lastLoginAt,
                                    Instant createdAt,
                                    Instant updatedAt) {
        return new User(id, organizationId, branchId, role, firstName, lastName,
                email, passwordHash, active, lastLoginAt, createdAt, updatedAt);
    }

    // ----------------------------------------------------------------------------------
    // Reglas de alcance (RN-12, RN-13)
    // ----------------------------------------------------------------------------------

    /**
     * Indica si el usuario puede realizar operaciones de escritura sobre una sucursal.
     *
     * <p>Esta es la comprobación que las anotaciones de método no pueden hacer: depende de
     * comparar la sucursal del recurso con la del usuario, y eso solo se sabe después de
     * cargar el recurso. Por eso los servicios la invocan explícitamente antes de escribir.
     *
     * <ul>
     *   <li>El administrador general opera sobre cualquier sucursal (RN-12).</li>
     *   <li>Gerente y operador solo sobre la suya (RN-13).</li>
     *   <li>Un usuario inactivo no opera sobre ninguna.</li>
     * </ul>
     */
    public boolean canOperateOnBranch(UUID targetBranchId) {
        if (!active) {
            return false;
        }
        if (role.canOperateOnAnyBranch()) {
            return true;
        }
        return branchId != null && branchId.equals(targetBranchId);
    }

    /**
     * Indica si el usuario puede consultar el inventario de una sucursal.
     *
     * <p>La lectura está abierta a toda la organización a propósito: es la capacidad que
     * permite localizar mercancía en la red antes de solicitar una transferencia (HU-06,
     * RF-06). La restricción por sucursal se aplica a la escritura, no a la consulta.
     */
    public boolean canViewBranch(UUID targetBranchId) {
        return active && targetBranchId != null;
    }

    /** Indica si el usuario administra usuarios, sucursales y configuración global. */
    public boolean canManageOrganization() {
        return active && role.canManageOrganization();
    }

    /** Indica si el usuario puede aprobar o ajustar transferencias sobre una sucursal. */
    public boolean canApproveTransferFrom(UUID originBranchId) {
        return active && role.canApproveTransfers() && canOperateOnBranch(originBranchId);
    }

    /** Indica si el usuario puede autenticarse. Una cuenta dada de baja no accede. */
    public boolean canAuthenticate() {
        return active;
    }

    // ----------------------------------------------------------------------------------
    // Comportamiento
    // ----------------------------------------------------------------------------------

    public void updateProfile(String firstName, String lastName) {
        this.firstName = requireName(firstName, "firstName", "El nombre es obligatorio.");
        this.lastName = requireName(lastName, "lastName", "El apellido es obligatorio.");
        touch();
    }

    /**
     * Reasigna el usuario a otra sucursal o a otro rol (HU-03).
     *
     * <p>Ambos cambios van juntos porque están acoplados: pasar a administrador libera la
     * sucursal, y dejar de serlo obliga a asignar una. Permitirlos por separado dejaría
     * estados intermedios inválidos, como un gerente sin sucursal.
     */
    public void reassign(Role newRole, UUID newBranchId) {
        Objects.requireNonNull(newRole, "El usuario debe tener un rol asignado.");
        this.branchId = requireBranchConsistentWithRole(newBranchId, newRole);
        this.role = newRole;
        touch();
    }

    /** Sustituye el hash de la contraseña. Recibe el hash, jamás la contraseña en claro. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = requirePasswordHash(newPasswordHash);
        touch();
    }

    /**
     * Registra un acceso correcto.
     *
     * <p>No invoca {@code touch()}: iniciar sesión no modifica la ficha del usuario, y
     * mezclar ambas marcas impediría distinguir "cuándo se editó este usuario" de "cuándo
     * entró por última vez".
     */
    public void recordSuccessfulLogin() {
        this.lastLoginAt = Instant.now();
    }

    /** Da de baja la cuenta. Es baja lógica: el usuario aparece en el histórico (RN-11). */
    public void deactivate() {
        if (!active) {
            return;
        }
        this.active = false;
        touch();
    }

    public void activate() {
        if (active) {
            return;
        }
        this.active = true;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ----------------------------------------------------------------------------------
    // Validación de invariantes
    // ----------------------------------------------------------------------------------

    /**
     * Un rol que opera dentro de una sucursal necesita tenerla asignada.
     *
     * <p>Un gerente o un operador sin sucursal no podrían registrar nada, porque toda
     * escritura pertenece a una sucursal concreta (RN-02). El administrador general es la
     * excepción legítima, y se le fuerza a no tener ninguna para que su alcance quede
     * inequívoco: opera sobre todas.
     */
    private static UUID requireBranchConsistentWithRole(UUID branchId, Role role) {
        if (role.code().requiresBranch() && branchId == null) {
            throw new DomainValidationException("branchId",
                    "Un usuario con rol '%s' debe estar asignado a una sucursal."
                            .formatted(role.code()));
        }
        if (!role.code().requiresBranch() && branchId != null) {
            throw new DomainValidationException("branchId",
                    "Un usuario con rol '%s' no puede estar asignado a una sucursal, "
                            .formatted(role.code())
                            + "ya que su alcance es toda la organización.");
        }
        return branchId;
    }

    private static String requireName(String value, String field, String message) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, message);
        }
        String normalized = value.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException(field,
                    "No puede superar %d caracteres.".formatted(NAME_MAX_LENGTH));
        }
        return normalized;
    }

    /**
     * Normaliza el correo a minúsculas.
     *
     * <p>Sin esto, {@code Ana@empresa.com} y {@code ana@empresa.com} serían cuentas
     * distintas para el índice único y la misma persona para quien intenta entrar.
     *
     * <p>La comprobación de formato es deliberadamente laxa: validar direcciones de correo
     * con expresiones regulares estrictas rechaza direcciones legítimas. La validación
     * seria de una dirección es enviarle un mensaje.
     */
    private static String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new DomainValidationException("email", "El correo electrónico es obligatorio.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > EMAIL_MAX_LENGTH) {
            throw new DomainValidationException("email",
                    "El correo no puede superar %d caracteres.".formatted(EMAIL_MAX_LENGTH));
        }
        int at = normalized.indexOf('@');
        if (at <= 0 || at == normalized.length() - 1 || normalized.indexOf('@', at + 1) >= 0) {
            throw new DomainValidationException("email",
                    "El correo electrónico no tiene un formato válido.");
        }
        return normalized;
    }

    private static String requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new DomainValidationException("password",
                    "La contraseña cifrada es obligatoria.");
        }
        if (passwordHash.length() > PASSWORD_HASH_MAX_LENGTH) {
            throw new DomainValidationException("password",
                    "El hash de la contraseña excede la longitud admitida.");
        }
        return passwordHash;
    }

    // ----------------------------------------------------------------------------------
    // Accesores
    // ----------------------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    /** Sucursal asignada. Nulo para el administrador general, cuyo alcance es la organización. */
    public UUID getBranchId() {
        return branchId;
    }

    public Role getRole() {
        return role;
    }

    public RoleCode getRoleCode() {
        return role.code();
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getEmail() {
        return email;
    }

    /**
     * Hash de la contraseña.
     *
     * <p>Solo debe consumirlo el puerto que verifica credenciales. Jamás se incluye en un
     * DTO de respuesta, en un log ni en un mensaje de error (RNF-03).
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof User user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Nunca incluye el hash de la contraseña: este texto acaba en logs y trazas. */
    @Override
    public String toString() {
        return "User[id=%s, email=%s, role=%s, branchId=%s, active=%s]"
                .formatted(id, email, role.code(), branchId, active);
    }
}
