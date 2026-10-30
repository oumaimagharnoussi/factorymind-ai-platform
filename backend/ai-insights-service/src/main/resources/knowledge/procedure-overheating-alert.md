---
title: Responding to a temperature alert
kind: PROCEDURE
machines:
---

> Demo content written for FactoryMind. Illustrative only.

Temperature sensors in FactoryMind watch motor windings, bearing housings and hydraulic oil. The plant default limits are 75 °C for the warning and 90 °C for the critical alert; class F motor insulation tolerates more, but every 10 °C above the design temperature roughly halves the life of insulation and lubricants.

## When a WARNING temperature alert is raised (above 75 °C)

1. Check the ambient temperature and the load: a hot afternoon or a heavy product can explain a few degrees. Compare with the same machine yesterday on the Live monitoring screen.
2. Check the cooling: motor fan cover free of dust, cooling fins clean, cabinet filters not clogged, oil cooler fan running, cooling water flowing.
3. Check lubrication: bearing grease interval respected, oil level and condition in gearboxes and hydraulic tanks.
4. Check the electrical supply on motors: unbalanced phases (more than 2 % voltage difference) or a single phase lost make a motor overheat. Ask an electrician to measure the currents.
5. Plan an inspection work order if the cause is not found and fixed within the shift.

## When a CRITICAL temperature alert is raised (above 90 °C)

1. Reduce the load or stop the machine at the end of the cycle; stop at once if there is smoke or a smell of burnt insulation.
2. Never open a hot hydraulic circuit or touch a hot motor housing: wait until it is below 50 °C, and apply lockout/tagout before any work.
3. Find the cause with the machine manual's troubleshooting table, fix it, then restart under supervision and follow the temperature for one hour.

## What not to do

Do not cool a hot motor or bearing with water or compressed air, and do not raise the alarm limit to make the alert go away: the limits in FactoryMind are changed only by the reliability engineer.
