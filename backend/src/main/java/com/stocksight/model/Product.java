package com.stocksight.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;

@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(name = "uk_products_sku", columnNames = "sku"))
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Column(nullable = false, length = 160)
    private String name;
    @NotBlank @Column(nullable = false, unique = true, length = 80)
    private String sku;
    @Min(0) @Column(nullable = false)
    private int openingBalance;
    @Min(0) @Column(nullable = false)
    private int safetyThreshold;
    @Min(0) @Column(nullable = false)
    private int currentStock;
    @Version private Long version;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Product() {}
    public Product(String name, String sku, int openingBalance, int safetyThreshold) {
        this.name = name; this.sku = sku; this.openingBalance = openingBalance;
        this.safetyThreshold = safetyThreshold; this.currentStock = openingBalance;
    }
    public Long getId(){return id;} public String getName(){return name;} public String getSku(){return sku;}
    public int getOpeningBalance(){return openingBalance;} public int getSafetyThreshold(){return safetyThreshold;}
    public int getCurrentStock(){return currentStock;} public Long getVersion(){return version;}
    public Instant getCreatedAt(){return createdAt;}
    public void setName(String v){name=v;} public void setSku(String v){sku=v;}
    public void setOpeningBalance(int v){openingBalance=v;} public void setSafetyThreshold(int v){safetyThreshold=v;}
    public void setCurrentStock(int v){currentStock=v;}
}
