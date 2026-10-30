---
title: FANUC M-20iD packaging robot — maintenance guide
kind: MANUAL
machines: M-20iD
---

> Demo content written for FactoryMind. Illustrative, not the manufacturer's manual.

The M-20iD is a six-axis articulated robot with a 25 kg payload, used on packaging line B to palletise cartons. Sensors: temperature of the J2 axis motor (warning 75 °C, critical 90 °C) and vibration of the base (warning 4.5 mm/s, critical 7.1 mm/s).

## Safety

Never enter the robot cell while the robot is in automatic mode. To work inside: put the controller in T1 mode, hold the teach pendant with its deadman switch, or switch off the controller and apply lockout/tagout. The arm can drop when the brakes are released: support the arm before releasing a brake or removing a motor.

## Troubleshooting

### Axis motor temperature high

The usual cause is a cycle that asks too much of one axis: high acceleration with a heavy load, or a carton weight above the payload set in the program. Check the payload setting, reduce the acceleration override, and check for collisions or rubbing cables in the dress pack. A reducer short of grease also overheats: check the grease interval.

### Vibration or shaking of the arm

Check the base fixing bolts, the gripper mounting bolts, and the payload data. Vibration only at the end of fast moves usually means wrong payload data or worn reducers.

## Preventive maintenance

| Interval | Task |
| --- | --- |
| Daily | Check cables and gripper hoses, clean the vacuum cups |
| 3 months | Check cable dress pack and mechanical stoppers |
| 1 year | Replace controller batteries (absolute encoders) |
| 3 years or 11,520 h | Replace reducer grease on J1 to J6 |
