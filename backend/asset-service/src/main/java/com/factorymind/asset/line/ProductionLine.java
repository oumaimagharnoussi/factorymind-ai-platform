package com.factorymind.asset.line;

import com.factorymind.asset.common.BaseEntity;
import com.factorymind.asset.factory.Factory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "production_lines")
public class ProductionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factory_id", nullable = false)
    private Factory factory;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineStatus status;

    protected ProductionLine() {
    }

    public ProductionLine(Factory factory, String name, LineStatus status) {
        this.factory = factory;
        update(name, status);
    }

    public void update(String name, LineStatus status) {
        this.name = name;
        this.status = status;
    }

    public Factory getFactory() {
        return factory;
    }

    public String getName() {
        return name;
    }

    public LineStatus getStatus() {
        return status;
    }
}
