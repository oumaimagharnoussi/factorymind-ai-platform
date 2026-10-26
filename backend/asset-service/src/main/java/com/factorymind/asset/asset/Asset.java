package com.factorymind.asset.asset;

import com.factorymind.asset.common.BaseEntity;
import com.factorymind.asset.line.ProductionLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** A machine on a production line, identified across the platform by its asset code (e.g. M-104). */
@Entity
@Table(name = "assets")
public class Asset extends BaseEntity {

    @Column(name = "asset_code", nullable = false, unique = true, length = 40)
    private String assetCode;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private ProductionLine line;

    @Column(length = 120)
    private String manufacturer;

    @Column(length = 120)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Criticality criticality;

    @Column(name = "installed_at")
    private LocalDate installedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status;

    protected Asset() {
    }

    public Asset(String assetCode, String name, ProductionLine line, String manufacturer, String model,
                 Criticality criticality, LocalDate installedAt) {
        this.assetCode = assetCode;
        this.status = AssetStatus.OPERATIONAL;
        update(name, line, manufacturer, model, criticality, installedAt);
    }

    public void update(String name, ProductionLine line, String manufacturer, String model,
                       Criticality criticality, LocalDate installedAt) {
        this.name = name;
        this.line = line;
        this.manufacturer = manufacturer;
        this.model = model;
        this.criticality = criticality;
        this.installedAt = installedAt;
    }

    public void changeStatus(AssetStatus status) {
        this.status = status;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public String getName() {
        return name;
    }

    public ProductionLine getLine() {
        return line;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
    }

    public Criticality getCriticality() {
        return criticality;
    }

    public LocalDate getInstalledAt() {
        return installedAt;
    }

    public AssetStatus getStatus() {
        return status;
    }
}
