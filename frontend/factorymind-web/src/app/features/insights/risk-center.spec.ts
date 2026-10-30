import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AssetDirectory } from '../../core/api/asset-directory';
import { InsightsApi } from '../../core/api/insights-api';
import { MachineRisk } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { RiskCenter } from './risk-center';

function risk(
  assetId: string,
  riskScore: number,
  riskLevel: MachineRisk['riskLevel'],
  driver: string,
): MachineRisk {
  return {
    assetId,
    riskScore,
    riskLevel,
    horizonHours: 24,
    mainSensorType: 'TEMPERATURE',
    hoursToCritical: 0.4,
    modelVersion: 'rf-24h-test',
    assessedAt: '2026-10-28T10:00:00Z',
    sensors: [
      {
        sensorType: 'TEMPERATURE',
        unit: '°C',
        currentValue: 74.2,
        failureProbability: riskScore,
        riskLevel,
        trendPerHour: 228,
        hoursToCritical: 0.4,
        drivers: [driver],
      },
    ],
  };
}

describe('RiskCenter', () => {
  function render() {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: Auth, useValue: { hasAnyRole: () => true } },
        {
          provide: AssetDirectory,
          useValue: { code: (id: string) => id.toUpperCase(), name: () => 'Hydraulic press' },
        },
        {
          provide: InsightsApi,
          useValue: {
            risks: () =>
              of([
                risk('m-102', 0.91, 'HIGH', 'trend reaches the critical limit in about 24 min'),
                risk('m-101', 0.12, 'LOW', 'stable at 59% of the critical limit'),
              ]),
            history: () => of([]),
            modelCard: () => of(null),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(RiskCenter);
    fixture.detectChanges();
    return fixture;
  }

  it('lists machines with their risk and the reason behind it, and opens the riskiest one', () => {
    const page: HTMLElement = render().nativeElement;

    expect(page.querySelector('.counter.bad')?.textContent).toContain('1 high risk');
    const machines = [...page.querySelectorAll('.machine')].map((m) => m.textContent ?? '');
    expect(machines[0]).toContain('M-102');
    expect(machines[0]).toContain('91%');
    expect(machines[0]).toContain('trend reaches the critical limit in about 24 min');
    expect(page.querySelector('.detail')?.textContent).toContain('about 24 min');
  });

  it('sends questions about the selected machine to the copilot', () => {
    const page: HTMLElement = render().nativeElement;

    const link = [...page.querySelectorAll('.report-actions a')].find((a) =>
      a.textContent?.includes('Ask the copilot'),
    );
    expect(link?.getAttribute('href')).toBe('/copilot?asset=m-102');
  });
});
