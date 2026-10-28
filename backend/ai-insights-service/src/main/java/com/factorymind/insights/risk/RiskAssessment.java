package com.factorymind.insights.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** One scoring of one machine: the history behind the risk chart, and the last level seen after a restart. */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID assetId;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskLevel riskLevel;

    @Column(nullable = false, length = 30)
    private String mainSensorType;

    private Double hoursToCritical;

    @Column(nullable = false, length = 40)
    private String modelVersion;

    @Column(nullable = false)
    private Instant assessedAt;

    protected RiskAssessment() {
    }

    public RiskAssessment(Prediction prediction, Instant assessedAt) {
        this.id = UUID.randomUUID();
        this.assetId = prediction.assetId();
        this.riskScore = BigDecimal.valueOf(prediction.riskScore()).setScale(3, RoundingMode.HALF_UP);
        this.riskLevel = prediction.riskLevel();
        this.mainSensorType = prediction.mainSensorType();
        this.hoursToCritical = prediction.mainSensor().hoursToCritical();
        this.modelVersion = prediction.modelVersion();
        this.assessedAt = assessedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getMainSensorType() {
        return mainSensorType;
    }

    public Double getHoursToCritical() {
        return hoursToCritical;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }
}
