package com.stocksight.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Entity
@Table(name = "stock_movements", indexes = {
    @Index(name = "idx_movement_product_date", columnList = "product_id,bill_date")
})
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private MovementType movementType;
    @Min(1) @Column(nullable = false)
    private int quantity;
    @NotNull @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;
    @NotNull @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    protected StockMovement() {}
    public StockMovement(Product product, MovementType movementType, int quantity, LocalDate billDate, LocalDate deliveryDate) {
        this.product=product; this.movementType=movementType; this.quantity=quantity;
        this.billDate=billDate; this.deliveryDate=deliveryDate;
    }
    public Long getId(){return id;} public Product getProduct(){return product;}
    public MovementType getMovementType(){return movementType;} public int getQuantity(){return quantity;}
    public LocalDate getBillDate(){return billDate;} public LocalDate getDeliveryDate(){return deliveryDate;}
}
