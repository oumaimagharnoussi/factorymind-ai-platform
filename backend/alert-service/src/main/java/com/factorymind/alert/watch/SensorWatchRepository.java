package com.factorymind.alert.watch;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SensorWatchRepository extends JpaRepository<SensorWatch, SensorKey> {

    /** Sensors whose timers can still raise or clear something just because time passes. */
    @Query("""
            select w.key from SensorWatch w
            where w.warning.aboveSince is not null or w.warning.belowSince is not null
               or w.critical.aboveSince is not null or w.critical.belowSince is not null""")
    List<SensorKey> findKeysInEpisode();
}
