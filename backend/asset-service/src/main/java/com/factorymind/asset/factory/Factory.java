package com.factorymind.asset.factory;

import com.factorymind.asset.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "factories")
public class Factory extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 200)
    private String location;

    /** IANA zone, e.g. Africa/Tunis. Timestamps are stored in UTC and converted for display. */
    @Column(nullable = false, length = 60)
    private String timezone;

    protected Factory() {
    }

    public Factory(String name, String location, String timezone) {
        update(name, location, timezone);
    }

    public void update(String name, String location, String timezone) {
        this.name = name;
        this.location = location;
        this.timezone = timezone;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public String getTimezone() {
        return timezone;
    }
}
