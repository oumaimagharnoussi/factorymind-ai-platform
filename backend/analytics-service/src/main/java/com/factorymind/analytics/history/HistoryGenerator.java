package com.factorymind.analytics.history;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Simulated past months for the demo machines, built the way the live pipeline would have recorded them:
 * failures (a critical alert, a corrective work order, unplanned downtime), monthly preventive services, the
 * model's HIGH-risk predictions with their outcome, and hourly sensor summaries that drift towards the critical
 * limit before each failure.
 * <p>
 * The story it tells is deliberately simple so the reports have something to show: failures become rarer over the
 * months, the second model version catches more of them, and a predicted failure is repaired faster (the parts
 * are ready). The same seed always gives the same history. None of it is real plant data.
 */
public class HistoryGenerator {

    static final String MODEL_V1 = "rf-24h-sim-v1";
    static final String MODEL_V2 = "rf-24h-sim-v2";
    static final List<String> TECHNICIANS = List.of("engineer", "a.benali", "s.trabelsi", "m.haddad");

    /** The telemetry simulator sends a reading every 3 seconds. */
    static final int READINGS_PER_HOUR = 1200;

    private static final Map<String, Double> MTBF_DAYS = Map.of("CRITICAL", 16.0, "HIGH", 22.0, "MEDIUM", 32.0,
            "LOW", 45.0);
    private static final Map<String, Integer> SENSOR_WEIGHT = Map.of("TEMPERATURE", 4, "VIBRATION", 4,
            "PRESSURE", 2, "ROTATION_SPEED", 2);
    /** Normal running value as a share of the warning limit. */
    private static final Map<String, Double> NORMAL_SHARE = Map.of("TEMPERATURE", 0.78, "VIBRATION", 0.5,
            "PRESSURE", 0.83, "ROTATION_SPEED", 0.955);

    public record Asset(int assetKey, String assetCode, String criticality, List<Sensor> sensors) {
    }

    public record Sensor(String sensorType, String unit, double warning, double critical) {
    }

    /** {@code failedAt} is set on corrective orders only. */
    public record WorkOrder(UUID id, String code, int assetKey, String technician, int failureTypeKey,
                            String type, String category, String origin, String priority, Instant failedAt,
                            Instant createdAt, Instant startedAt, Instant completedAt, BigDecimal partsCost,
                            BigDecimal laborCost) {
    }

    public record Prediction(UUID id, int assetKey, String modelVersion, String outcome, Instant predictedAt,
                             BigDecimal riskScore, String sensorType, Instant failureAt, BigDecimal leadTimeHours) {
    }

    public record TelemetryHour(int assetKey, String sensorType, Instant hourStart, String unit, int readings,
                                BigDecimal avg, BigDecimal min, BigDecimal max, int warningReadings,
                                int criticalReadings) {
    }

    public record History(List<WorkOrder> workOrders, List<Prediction> predictions,
                          List<TelemetryHour> telemetry) {

        public int size() {
            return workOrders.size() + predictions.size() + telemetry.size();
        }
    }

    private record Failure(int assetKey, String sensorType, Instant at) {
    }

    private record Stop(Instant from, Instant to) {
    }

    private final Random random;
    private final BigDecimal laborRate;
    private final Duration predictionWindow;

    public HistoryGenerator(long seed, BigDecimal laborRate, Duration predictionWindow) {
        this.random = new Random(seed);
        this.laborRate = laborRate;
        this.predictionWindow = predictionWindow;
    }

    /** History of {@code [start, end)}; every work order in it is completed before {@code end}. */
    public History generate(List<Asset> assets, Instant start, Instant end) {
        List<WorkOrder> orders = new ArrayList<>();
        List<Prediction> predictions = new ArrayList<>();
        List<TelemetryHour> telemetry = new ArrayList<>();
        Instant middle = start.plus(Duration.between(start, end).dividedBy(2));

        for (Asset asset : assets.stream().sorted(Comparator.comparing(Asset::assetCode)).toList()) {
            List<Failure> failures = new ArrayList<>();
            List<Stop> stops = new ArrayList<>();
            failures(asset, start, end, middle, orders, predictions, failures, stops);
            falseAlarms(asset, start, end, middle, failures, orders, predictions);
            preventive(asset, start, end, orders, stops);
            telemetry(asset, start, end, failures, stops, telemetry);
        }
        orders.sort(Comparator.comparing(WorkOrder::createdAt));
        List<WorkOrder> numbered = new ArrayList<>(orders.size());
        for (int i = 0; i < orders.size(); i++) {
            WorkOrder o = orders.get(i);
            numbered.add(new WorkOrder(o.id(), "SIM-%05d".formatted(i + 1), o.assetKey(), o.technician(),
                    o.failureTypeKey(), o.type(), o.category(), o.origin(), o.priority(), o.failedAt(),
                    o.createdAt(), o.startedAt(), o.completedAt(), o.partsCost(), o.laborCost()));
        }
        return new History(numbered, predictions, telemetry);
    }

    private void failures(Asset asset, Instant start, Instant end, Instant middle, List<WorkOrder> orders,
                          List<Prediction> predictions, List<Failure> failures, List<Stop> stops) {
        double mtbfDays = MTBF_DAYS.getOrDefault(asset.criticality(), 30.0);
        double spanHours = Duration.between(start, end).toHours();
        Instant t = start;
        while (true) {
            double progress = Duration.between(start, t).toHours() / spanHours;
            double rateFactor = 1.3 - 0.6 * progress;           // fewer failures as the months go by
            t = t.plus(hours(-Math.log(1 - random.nextDouble()) * mtbfDays * 24 / rateFactor));
            if (!t.isBefore(end.minus(Duration.ofDays(1)))) {
                return;
            }
            Sensor sensor = pickSensor(asset);
            boolean reported = random.nextDouble() < 0.1;       // noticed by an operator, no sensor alarm
            String model = t.isBefore(middle) ? MODEL_V1 : MODEL_V2;
            boolean predicted = random.nextDouble() < (MODEL_V1.equals(model) ? 0.55 : 0.8) && !reported
                    && t.isAfter(start.plus(Duration.ofDays(1)));   // the prediction must fall inside the period
            double speedUp = predicted ? 0.6 : 1.0;

            Instant failedAt = t;
            Instant createdAt = failedAt.plus(Duration.ofMinutes(reported ? 20 : 1));
            double response = (0.25 + random.nextDouble() * 1.75) * speedUp;
            if (!predicted && random.nextDouble() < 0.15) {
                response += 6 + random.nextDouble() * 14;      // the part was not in stock
            }
            Instant startedAt = failedAt.plus(hours(response));
            double repairHours = (1.5 + random.nextDouble() * 4.5) * ("CRITICAL".equals(asset.criticality()) ? 1.4 : 1)
                    * (predicted ? 0.65 : 1);
            Instant completedAt = startedAt.plus(hours(repairHours));
            if (!completedAt.isBefore(end)) {
                return;
            }
            boolean urgent = "CRITICAL".equals(asset.criticality()) || "HIGH".equals(asset.criticality());
            orders.add(new WorkOrder(id(), null, asset.assetKey(), technician(),
                    reported ? 0 : failureTypeKey(sensor.sensorType()), "CORRECTIVE", "CORRECTIVE",
                    reported ? "MANUAL" : "ALERT", urgent ? "URGENT" : "HIGH", failedAt, createdAt, startedAt,
                    completedAt, money((80 + random.nextDouble() * 900) * (predicted ? 0.7 : 1)),
                    labor(startedAt, completedAt)));
            failures.add(new Failure(asset.assetKey(), sensor.sensorType(), failedAt));
            stops.add(new Stop(failedAt, completedAt));

            if (predicted) {
                double lead = 4 + random.nextDouble() * 16;
                Instant predictedAt = failedAt.minus(hours(lead));
                predictions.add(new Prediction(id(), asset.assetKey(), model, "TRUE_POSITIVE",
                        predictedAt, score(0.7, 0.97), sensor.sensorType(), failedAt, decimal(lead)));
                inspection(asset, predictedAt, failedAt, orders);
            } else if (!reported) {
                predictions.add(new Prediction(id(), asset.assetKey(), model, "MISSED", null, null,
                        sensor.sensorType(), failedAt, null));
            }
            t = completedAt;
        }
    }

    private void falseAlarms(Asset asset, Instant start, Instant end, Instant middle, List<Failure> failures,
                             List<WorkOrder> orders, List<Prediction> predictions) {
        Instant t = start;
        while (true) {
            double meanDays = t.isBefore(middle) ? 45 : 70;
            t = t.plus(hours(-Math.log(1 - random.nextDouble()) * meanDays * 24));
            if (!t.isBefore(end.minus(predictionWindow))) {
                return;
            }
            Instant at = t;
            boolean failureFollows = failures.stream()
                    .anyMatch(f -> f.at().isAfter(at.minus(predictionWindow)) && !f.at().isAfter(at.plus(predictionWindow)));
            if (failureFollows) {
                continue;
            }
            predictions.add(new Prediction(id(), asset.assetKey(), t.isBefore(middle) ? MODEL_V1 : MODEL_V2,
                    "FALSE_POSITIVE", at, score(0.62, 0.85), pickSensor(asset).sensorType(), null, null));
            inspection(asset, at, at.plus(Duration.ofHours(12)), orders);
        }
    }

    /** The inspection ai-insights-service plans when a machine turns HIGH. */
    private void inspection(Asset asset, Instant predictedAt, Instant before, List<WorkOrder> orders) {
        Instant started = predictedAt.plus(hours(0.5 + random.nextDouble() * 2));
        if (started.isAfter(before)) {
            started = predictedAt.plus(Duration.ofMinutes(15));
        }
        Instant completed = started.plus(hours(0.5 + random.nextDouble()));
        orders.add(new WorkOrder(id(), null, asset.assetKey(), technician(), 5, "INSPECTION",
                "PLANNED", "AI", "HIGH", null, predictedAt, started, completed, money(random.nextDouble() * 60),
                labor(started, completed)));
    }

    private void preventive(Asset asset, Instant start, Instant end, List<WorkOrder> orders, List<Stop> stops) {
        Instant day = start.plus(Duration.ofDays(random.nextInt(30))).truncatedTo(ChronoUnit.DAYS);
        while (day.isBefore(end.minus(Duration.ofDays(1)))) {
            Instant started = day.plus(hours(6 + random.nextDouble() * 4));
            Instant completed = started.plus(hours(1.5 + random.nextDouble() * 2.5));
            boolean clash = stops.stream().anyMatch(s -> s.from().isBefore(completed) && s.to().isAfter(started));
            if (!clash) {
                orders.add(new WorkOrder(id(), null, asset.assetKey(), technician(), 6, "PREVENTIVE",
                        "PLANNED", "MANUAL", "MEDIUM", null, latest(start, day.minus(Duration.ofDays(3))), started,
                        completed,
                        money(40 + random.nextDouble() * 210), labor(started, completed)));
                stops.add(new Stop(started, completed));
            }
            day = day.plus(Duration.ofDays(26 + random.nextInt(9)));
        }
    }

    private void telemetry(Asset asset, Instant start, Instant end, List<Failure> failures, List<Stop> stops,
                           List<TelemetryHour> rows) {
        for (Instant hour = start; hour.isBefore(end); hour = hour.plus(Duration.ofHours(1))) {
            Instant from = hour;
            Instant to = hour.plus(Duration.ofHours(1));
            if (stops.stream().anyMatch(s -> !s.from().isAfter(from) && !s.to().isBefore(to))) {
                continue;                                       // machine stopped the whole hour: no readings
            }
            int hourOfDay = (int) ((hour.getEpochSecond() / 3600) % 24);
            for (Sensor sensor : asset.sensors()) {
                double normal = sensor.warning() * NORMAL_SHARE.getOrDefault(sensor.sensorType(), 0.75);
                double daily = 0.03 * Math.sin(2 * Math.PI * (hourOfDay - 6) / 24.0);
                double avg = normal * (1 + daily + random.nextGaussian() * 0.012);
                double drift = drift(asset, sensor, failures, to);
                avg += (sensor.critical() * 1.03 - normal) * drift;
                double spread = normal * 0.025 + (sensor.critical() - normal) * drift * 0.3;
                double min = avg - spread;
                double max = avg + spread;
                rows.add(new TelemetryHour(asset.assetKey(), sensor.sensorType(), hour, sensor.unit(),
                        READINGS_PER_HOUR, decimal3(avg), decimal3(min), decimal3(max),
                        above(sensor.warning(), min, max), above(sensor.critical(), min, max)));
            }
        }
    }

    /** 0 in normal running, rising to 1 over the 24 hours before a failure of this sensor. */
    private static double drift(Asset asset, Sensor sensor, List<Failure> failures, Instant at) {
        double worst = 0;
        for (Failure f : failures) {
            if (f.assetKey() == asset.assetKey() && f.sensorType().equals(sensor.sensorType())
                    && !at.isAfter(f.at()) && at.isAfter(f.at().minus(Duration.ofHours(24)))) {
                double share = 1 - Duration.between(at, f.at()).toMinutes() / (24 * 60.0);
                worst = Math.max(worst, share * share);
            }
        }
        return worst;
    }

    /** Readings above a limit, assuming they spread evenly between the hour's min and max. */
    private static int above(double limit, double min, double max) {
        if (max <= limit) {
            return 0;
        }
        double share = min >= limit ? 1 : (max - limit) / (max - min);
        return (int) Math.round(share * READINGS_PER_HOUR);
    }

    private Sensor pickSensor(Asset asset) {
        if (asset.sensors().isEmpty()) {
            return new Sensor("TEMPERATURE", "°C", 75, 90);
        }
        int total = asset.sensors().stream().mapToInt(s -> SENSOR_WEIGHT.getOrDefault(s.sensorType(), 1)).sum();
        int pick = random.nextInt(total);
        for (Sensor sensor : asset.sensors()) {
            pick -= SENSOR_WEIGHT.getOrDefault(sensor.sensorType(), 1);
            if (pick < 0) {
                return sensor;
            }
        }
        return asset.sensors().getLast();
    }

    /** Same keys as dw.dim_failure_type. */
    static int failureTypeKey(String sensorType) {
        return switch (sensorType) {
            case "TEMPERATURE" -> 1;
            case "VIBRATION" -> 2;
            case "PRESSURE" -> 3;
            case "ROTATION_SPEED" -> 4;
            default -> 0;
        };
    }

    /** Random ids would change the history on every install; these come from the seeded generator. */
    private UUID id() {
        return new UUID(random.nextLong(), random.nextLong());
    }

    private String technician() {
        return TECHNICIANS.get(random.nextInt(TECHNICIANS.size()));
    }

    private BigDecimal labor(Instant started, Instant completed) {
        BigDecimal hours = BigDecimal.valueOf(Duration.between(started, completed).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return hours.multiply(laborRate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal score(double low, double high) {
        return BigDecimal.valueOf(low + random.nextDouble() * (high - low)).setScale(3, RoundingMode.HALF_UP);
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Duration hours(double hours) {
        return Duration.ofSeconds(Math.round(hours * 3600));
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal3(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }
}
