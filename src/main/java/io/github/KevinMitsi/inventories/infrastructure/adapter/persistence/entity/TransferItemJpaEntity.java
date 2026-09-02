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
@Table(name = "transfer_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferItemJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false, updatable = false)
    private TransferJpaEntity transfer;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "requested_quantity", nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal requestedQuantity;

    @Column(name = "approved_quantity", precision = 18, scale = 6)
    private BigDecimal approvedQuantity;

    @Column(name = "shipped_quantity", precision = 18, scale = 6)
    private BigDecimal shippedQuantity;

    @Column(name = "received_quantity", precision = 18, scale = 6)
    private BigDecimal receivedQuantity;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TransferItemJpaEntity entity && id != null && id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
