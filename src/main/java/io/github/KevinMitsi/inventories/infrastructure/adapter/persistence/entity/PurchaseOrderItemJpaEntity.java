package io.github.KevinMitsi.inventories.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItemJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false, updatable = false)
    private PurchaseOrderJpaEntity purchaseOrder;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_unit_id", nullable = false, updatable = false)
    private UUID productUnitId;

    @Column(name = "quantity", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal quantity;

    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 6)
    private BigDecimal receivedQuantity;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "discount_percentage", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof PurchaseOrderItemJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
