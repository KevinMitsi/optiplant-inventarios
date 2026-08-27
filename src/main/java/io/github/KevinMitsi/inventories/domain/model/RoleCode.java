package io.github.KevinMitsi.inventories.domain.model;

import io.github.KevinMitsi.inventories.domain.exception.DomainValidationException;

import java.util.Locale;

/**
 * Los tres roles del sistema, con las reglas de alcance que cada uno implica.
 *
 * <p>Corresponden a los actores identificados en `PHASE1.md` §6. Es un enum y no una
 * consulta a la tabla `app_role` porque el conjunto es cerrado y, sobre todo, porque las
 * reglas de alcance —quién ve qué sucursales— viven en el código: una fila nueva en la
 * tabla no vendría acompañada de la lógica que la interpretase.
 *
 * <p>La tabla sigue existiendo para la integridad referencial y para los metadatos
 * descriptivos (nombre legible, descripción), que sí conviene tener en la base.
 *
 * <p>Las decisiones de alcance se expresan aquí como métodos en lugar de repartirse en
 * comparaciones sueltas por los servicios. Cuando la regla está en un solo sitio, cambiarla
 * es cambiar un método; cuando está esparcida en condicionales, es una cacería.
 */
public enum RoleCode {

    /**
     * Administrador general: visibilidad y operación sobre toda la organización (RN-12).
     *
     * <p>No pertenece a ninguna sucursal concreta, y por eso {@code app_user.branch_id}
     * admite nulos.
     */
    ADMIN,

    /**
     * Gerente de sucursal: responsable operativo de la suya (RN-13).
     *
     * <p>Puede consultar el inventario de otras sucursales —lo necesita para localizar
     * mercancía antes de pedir una transferencia (HU-06)— pero solo opera sobre la propia.
     */
    BRANCH_MANAGER,

    /** Operador de inventario: ejecuta las operaciones diarias dentro de su sucursal. */
    INVENTORY_OPERATOR;

    /**
     * Indica si el rol puede <em>operar</em> sobre cualquier sucursal (RN-12).
     *
     * <p>Es distinto de poder consultarlas: la lectura del inventario ajeno está abierta a
     * todos los roles por diseño, porque es lo que permite localizar stock en la red antes
     * de solicitar una transferencia.
     */
    public boolean canOperateOnAnyBranch() {
        return this == ADMIN;
    }

    /** Indica si el rol administra usuarios, sucursales y configuración global (RF-03, RF-05). */
    public boolean canManageOrganization() {
        return this == ADMIN;
    }

    /**
     * Indica si el rol puede aprobar o ajustar la cantidad de una transferencia (HU-29).
     *
     * <p>Comprometer stock de una sucursal es una decisión de supervisión, no una tarea de
     * ejecución, así que queda fuera del alcance del operador.
     */
    public boolean canApproveTransfers() {
        return this == ADMIN || this == BRANCH_MANAGER;
    }

    /** Indica si el rol puede resolver el faltante de una transferencia (HU-33). */
    public boolean canResolveTransferIssues() {
        return this == ADMIN || this == BRANCH_MANAGER;
    }

    /**
     * Indica si el rol debe tener una sucursal asignada.
     *
     * <p>Un gerente o un operador sin sucursal no podrían realizar ninguna operación, ya
     * que toda escritura pertenece a una sucursal concreta (RN-02). El administrador es la
     * excepción legítima.
     */
    public boolean requiresBranch() {
        return this != ADMIN;
    }

    /** Nombre de la autoridad tal como la espera Spring Security. */
    public String asAuthority() {
        return "ROLE_" + name();
    }

    /**
     * Convierte el código almacenado en el enum correspondiente.
     *
     * @throws DomainValidationException si el valor no corresponde a ningún rol conocido,
     *         lo que delataría un dato corrupto o un token manipulado
     */
    public static RoleCode fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("role", "El rol es obligatorio.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException cause) {
            throw new DomainValidationException("role",
                    "Rol desconocido: '%s'.".formatted(value));
        }
    }
}
