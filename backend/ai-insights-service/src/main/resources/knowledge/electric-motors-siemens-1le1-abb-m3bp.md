---
title: Low-voltage induction motors (Siemens 1LE1, ABB M3BP) — maintenance guide
kind: MANUAL
machines: 1LE1 IE3, M3BP 250
---

> Demo content written for FactoryMind. Illustrative, not the manufacturer's manual.

This guide covers the three-phase squirrel-cage induction motors of the plant: the Siemens 1LE1 IE3 driving the main conveyor (M-101) and the ABB M3BP 250 on the variable-speed drive of packaging line B (M-104). Both are IP55, totally enclosed and fan cooled, insulation class F with class B temperature rise. Sensors: bearing temperature (warning 75 °C, critical 90 °C), vibration on the drive-end bearing (warning 4.5 mm/s, critical 7.1 mm/s), and for the M3BP the shaft speed (rated 1,485 rpm, warning 1,550 rpm, critical 1,650 rpm).

## Safety

Apply lockout/tagout on the motor isolator and, for M-104, on the variable-speed drive. The drive's DC bus capacitors stay charged after isolation: wait at least 5 minutes and check that the DC bus voltage is below 50 V before opening the terminal box.

## Bearing lubrication

Drive-end and non-drive-end bearings are regreasable (6316 C3 on the M3BP 250, 6309 C3 on the 1LE1 frame size 160).

1. Clean the grease nipple and open the grease outlet plug.
2. With the motor running (if it can be done safely) or just stopped, press in the quantity on the rating plate: 35 g for the M3BP 250, 20 g for the 1LE1.
3. Let the motor run 1 to 2 hours with the outlet open so excess grease leaves; bearing temperature rises for a while and then settles.
4. Interval: every 4,000 operating hours at 1,500 rpm, halved when the ambient is above 40 °C. Use a lithium-complex grease of NLGI grade 2; do not mix grease types.

Over-greasing is a common cause of hot bearings: too much grease churns and heats up. If a bearing runs hot right after greasing, check that the outlet plug was opened.

## Troubleshooting

### Bearing temperature high

Causes in order of likelihood: over-greasing or dry bearing, belt or coupling misalignment loading the bearing, blocked cooling (fan cover clogged with dust), worn bearing. A worn bearing usually shows rising vibration together with temperature.

### Vibration high

Check the coupling and its alignment first, then the foundation bolts, then the bearings. On the M3BP driven by a variable-speed drive, a resonance at one speed only can be avoided with the drive's skip-frequency parameter (to be set by the engineer).

### Speed above rated (M3BP on M-104)

The rotation speed sensor reading above 1,550 rpm means the variable-speed drive commands more than 50 Hz, or the speed sensor is faulty. Check the drive's speed reference and its maximum frequency parameter, then the sensor gap (1.0 to 1.5 mm) and its cable. An induction motor cannot run faster than its supply frequency allows, so a real overspeed points to the drive settings.

### Motor overheating or tripping

Measure the three phase currents; more than 10 % imbalance points to a supply problem or a winding fault. Measure insulation resistance with a 500 V megohmmeter: above 100 MΩ is good, below 10 MΩ the motor must be dried or rewound.

## Preventive maintenance

| Interval | Task |
| --- | --- |
| Monthly | Clean fan cover, check noise and vibration trend |
| 4,000 h | Regrease bearings |
| Yearly | Insulation resistance test, terminal tightness, coupling alignment check |
| 40,000 h | Replace bearings |
