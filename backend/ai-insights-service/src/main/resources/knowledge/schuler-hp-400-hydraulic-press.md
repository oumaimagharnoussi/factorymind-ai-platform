---
title: Schuler HP-400 hydraulic press — operation and maintenance
kind: MANUAL
machines: HP-400
---

> Demo content written for FactoryMind. Illustrative, not the manufacturer's manual.

The HP-400 is a 400-tonne hydraulic press. A variable-displacement axial piston pump driven by a 55 kW motor feeds the main cylinder through a proportional valve block. Nominal working pressure is 160 bar; the main relief valve opens at 230 bar. The tank holds 600 litres of HLP 46 hydraulic oil, cooled by an oil-to-air cooler. Sensors watched by FactoryMind: oil temperature (warning 75 °C, critical 90 °C), system pressure (warning 180 bar, critical 210 bar) and pump vibration (warning 4.5 mm/s, critical 7.1 mm/s).

## Safety

The press stores energy in the accumulator (up to 200 bar) and in the raised slide. Before any work: lower the slide onto the safety blocks, apply lockout/tagout on the main isolator and the hydraulic isolation valve V1, open the accumulator discharge valve V7 and wait until manometer M2 reads 0 bar. Never loosen a hydraulic fitting under pressure: an oil jet at 160 bar penetrates skin.

## Alarm codes

- E-210 Pressure above 180 bar during holding: check the proportional valve setting and the pressure transducer.
- E-211 Pressure above 210 bar: press stops; suspect a sticking proportional valve or a blocked return line. Do not raise the relief valve setting.
- E-217 Oil temperature above 75 °C: cooler fan, cooler fins, oil level, internal leakage of the pump.
- E-218 Oil temperature above 90 °C: press stops at end of cycle; let the oil cool below 50 °C before any work.
- E-230 Pump case drain flow too high: internal wear of the pump; plan pump replacement.

## Troubleshooting

### Oil overheats (E-217, E-218)

1. Check the cooler fan runs and the cooler fins are clean; blow them out from the clean side after lockout.
2. Check the oil level in the tank sight glass (between MIN and MAX with the slide up).
3. Check the return filter clogging indicator; a clogged filter sends oil through the bypass and heats it.
4. Feel the pump case drain line: a hot drain line and an E-230 history mean internal leakage of the pump, which turns pressure into heat. The pump must be overhauled.
5. Check that the press does not wait in holding at full pressure between cycles (pressure unloading must be active in idle).

### Pressure too high or unstable (E-210, E-211)

1. Compare the pressure transducer with manometer M1; replace the transducer if they differ by more than 5 bar.
2. Clean or replace the proportional valve pilot filter.
3. Check the return line to the tank is not blocked (closed valve, collapsed hose).

### Pump vibration rising

Rising vibration on the pump with oil temperature rising at the same time is the classic sign of a worn pump or of cavitation. Check the suction line for air leaks (foam in the tank, whining noise) and the suction strainer. Coupling misalignment between motor and pump also shows as vibration at 2x speed.

## Preventive maintenance

| Interval | Task |
| --- | --- |
| Daily | Oil level, leaks, manometer M1 reading at idle |
| Weekly | Clean cooler fins, check filter indicators |
| 2,000 h or 6 months | Replace return filter element (part HF-R-400-10) and pilot filter |
| 4,000 h or yearly | Oil analysis (water content, particle count ISO 4406 target 18/16/13); replace oil if out of limits |
| 8,000 h | Check accumulator pre-charge (nitrogen, 90 bar), replace hoses older than 6 years |
