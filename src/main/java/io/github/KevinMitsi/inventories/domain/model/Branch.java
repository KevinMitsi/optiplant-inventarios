package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sucursal de la organización.
 *
 * <p>Es un modelo de dominio puro: no conoce JPA, ni Jackson, ni Spring. La clase que
 * habla con la base de datos es {@code BranchJpaEntity}, en el adaptador de persistencia,
 * y un mapeador traduce entre ambas. Esa separación tiene un coste real —dos clases y un
 * mapeador por agregado— y compra dos cosas concretas: que las reglas de negocio se puedan
 * probar sin levantar contexto de Spring ni contenedor de base de datos, y que un cambio
 * de esquema no arrastre cambios en el dominio.
 *
 * <p>La sucursal es la unidad de autonomía operativa del sistema: el stock, las ventas, las
 * compras y los extremos de una transferencia pertenecen siempre a una sucursal concreta
 * (RN-02). También delimita la autorización, porque un gerente opera dentro de la suya
 * mientras que el administrador general las ve todas (RN-12, RN-13).
 *
 * <p>Los invariantes se comprueban en el constructor, de modo que no existe forma de
 * obtener una instancia en estado inválido. La identidad —{@code id}, {@code organizationId},
 * {@code code}, {@code createdAt}— es inmutable; lo demás cambia a través de métodos con
 * nombre de intención, nunca por medio de asignadores genéricos.
 */
public final class Branch {

    private static final int CODE_MAX_LENGTH = 30;
    private static final int NAME_MAX_LENGTH = 150;
    private static final int ADDRESS_MAX_LENGTH = 250;
    private static final int CITY_MAX_LENGTH = 100;
    private static final int PHONE_MAX_LENGTH = 30;

    private final UUID id;
    private final UUID organizationId;

    /**
     * Código de negocio de la sucursal, único dentro de su organización.
     *
     * <p>Es inmutable una vez creada: aparece en números de documento y en referencias
     * operativas, así que cambiarlo rompería la trazabilidad de lo ya registrado.
     */
    private final String code;

    private final Instant createdAt;

    private String name;
    private String addressLine;
    private String city;
    private String countryCode;
    private String phone;
    private boolean active;
    private Instant updatedAt;

    private Branch(UUID id,
                   UUID organizationId,
                   String code,
                   String name,
                   String addressLine,
                   String city,
                   String countryCode,
                   String phone,
                   boolean active,
                   Instant createdAt,
                   Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador de la sucursal no puede ser nulo.");
        this.organizationId = Objects.requireNonNull(organizationId,
                "La sucursal debe pertenecer a una organización.");
        this.code = requireCode(code);
        this.name = requireName(name);
        this.addressLine = normalizeOptional(addressLine, ADDRESS_MAX_LENGTH, "dirección");
        this.city = normalizeOptional(city, CITY_MAX_LENGTH, "ciudad");
        this.countryCode = requireCountryCode(countryCode);
        this.phone = normalizeOptional(phone, PHONE_MAX_LENGTH, "teléfono");
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creación no puede ser nula.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula.");
    }

    /**
     * Crea una sucursal nueva.
     *
     * <p>Nace activa: registrarla ya implica la intención de operar con ella. El
     * identificador se genera aquí y no en la base de datos porque con UUID el dominio
     * puede construir el agregado completo, relacionarlo y publicarlo antes de que exista
     * ninguna fila, sin depender de un valor que solo llega tras el INSERT.
     */
    public static Branch create(UUID organizationId,
                                String code,
                                String name,
                                String addressLine,
                                String city,
                                String countryCode,
                                String phone) {
        Instant now = Instant.now();
        return new Branch(UUID.randomUUID(), organizationId, code, name,
                addressLine, city, countryCode, phone, true, now, now);
    }

    /**
     * Reconstruye una sucursal ya persistida.
     *
     * <p>Lo usa exclusivamente el adaptador de persistencia al leer de la base. Se distingue
     * de {@link #create} porque no genera identificador ni marcas de tiempo: respeta los
     * que ya existen. Sigue validando los invariantes, de modo que un dato corrupto en la
     * base se detecta al cargarlo y no varias operaciones más tarde.
     */
    public static Branch reconstitute(UUID id,
                                      UUID organizationId,
                                      String code,
                                      String name,
                                      String addressLine,
                                      String city,
                                      String countryCode,
                                      String phone,
                                      boolean active,
                                      Instant createdAt,
                                      Instant updatedAt) {
        return new Branch(id, organizationId, code, name, addressLine, city,
                countryCode, phone, active, createdAt, updatedAt);
    }

    // ----------------------------------------------------------------------------------
    // Comportamiento
    // ----------------------------------------------------------------------------------

    /** Cambia los datos descriptivos. El código y la organización no se tocan nunca. */
    public void updateDetails(String name,
                              String addressLine,
                              String city,
                              String countryCode,
                              String phone) {
        this.name = requireName(name);
        this.addressLine = normalizeOptional(addressLine, ADDRESS_MAX_LENGTH, "dirección");
        this.city = normalizeOptional(city, CITY_MAX_LENGTH, "ciudad");
        this.countryCode = requireCountryCode(countryCode);
        this.phone = normalizeOptional(phone, PHONE_MAX_LENGTH, "teléfono");
        touch();
    }

    /**
     * Da de baja la sucursal.
     *
     * <p>Es baja lógica y no borrado físico (ENTITIES.md §30): la sucursal aparece en
     * ventas, compras, movimientos y transferencias históricas, y eliminarla dejaría ese
     * histórico sin poder explicarse. Una sucursal inactiva deja de admitir operaciones
     * nuevas, pero sus registros pasados siguen siendo consultables.
     *
     * <p>Es idempotente: desactivar algo ya desactivado no es un error.
     */
    public void deactivate() {
        if (!active) {
            return;
        }
        this.active = false;
        touch();
    }

    /** Reactiva una sucursal dada de baja. Idempotente. */
    public void activate() {
        if (active) {
            return;
        }
        this.active = true;
        touch();
    }

    /**
     * Indica si la sucursal admite operaciones que modifiquen inventario.
     *
     * <p>Lo consultan los servicios de venta, compra y transferencia antes de registrar
     * nada: una sucursal inactiva puede consultarse, pero no puede mover mercancía.
     */
    public boolean canOperate() {
        return active;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ----------------------------------------------------------------------------------
    // Validación de invariantes
    // ----------------------------------------------------------------------------------

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new DomainValidationException("code", "El código de la sucursal es obligatorio.");
        }
        // Se normaliza a mayúsculas para que la unicidad por organización no dependa de
        // cómo lo escribiera quien creó el registro.
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.length() > CODE_MAX_LENGTH) {
            throw new DomainValidationException("code",
                    "El código de la sucursal no puede superar %d caracteres.".formatted(CODE_MAX_LENGTH));
        }
        return normalized;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name", "El nombre de la sucursal es obligatorio.");
        }
        String normalized = name.trim();
        if (normalized.length() > NAME_MAX_LENGTH) {
            throw new DomainValidationException("name",
                    "El nombre de la sucursal no puede superar %d caracteres.".formatted(NAME_MAX_LENGTH));
        }
        return normalized;
    }

    private static String requireCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        String normalized = countryCode.trim().toUpperCase(java.util.Locale.ROOT);
        // La columna es CHAR(2): el código ISO 3166-1 alfa-2, como CO, MX o ES.
        if (normalized.length() != 2) {
            throw new DomainValidationException("countryCode",
                    "El código de país debe tener exactamente 2 caracteres (ISO 3166-1 alfa-2).");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength, String fieldLabel) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new DomainValidationException(fieldLabel,
                    "El campo %s no puede superar %d caracteres.".formatted(fieldLabel, maxLength));
        }
        return normalized;
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

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Igualdad por identidad, no por atributos.
     *
     * <p>Dos instancias con el mismo {@code id} son la misma sucursal aunque una tenga el
     * nombre ya modificado y la otra no. Es la semántica propia de una entidad, frente a
     * la de un objeto de valor como {@link Quantity}, que sí compara por contenido.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Branch branch && id.equals(branch.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Branch[id=%s, code=%s, name=%s, active=%s]".formatted(id, code, name, active);
    }
}
