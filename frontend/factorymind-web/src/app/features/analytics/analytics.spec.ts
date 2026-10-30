import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AnalyticsApi } from '../../core/api/analytics-api';
import { AnalyticsOverview } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { Analytics } from './analytics';

const overview: AnalyticsOverview = {
  from: '2026-08-01',
  to: '2026-10-30',
  factory: null,
  machines: 7,
  operatingHours: 15000,
  plannedDowntimeHours: 120,
  unplannedDowntimeHours: 210.5,
  availability: 0.986,
  failures: 23,
  mtbfHours: 643,
  mttrHours: 6.2,
  partsCost: 9800,
  laborCost: 2100,
  maintenanceCost: 11900,
  correctiveOrders: 23,
  plannedOrders: 40,
  predictions: {
    truePositives: 12,
    falseAlarms: 4,
    missedFailures: 6,
    pending: 0,
    precision: 0.75,
    recall: 0.6667,
    averageLeadTimeHours: 11.4,
  },
};

describe('Analytics', () => {
  function render(roles: string[], powerBiReportUrl: string | null = null) {
    const api = {
      overview: vi.fn(() => of(overview)),
      assets: vi.fn(() =>
        of([
          {
            assetCode: 'M-102',
            assetName: 'Hydraulic Press',
            factory: 'Sfax Plant',
            criticality: 'CRITICAL',
            failures: 6,
            unplannedDowntimeHours: 48.2,
            availability: 0.978,
            mtbfHours: 350,
            mttrHours: 8,
            maintenanceCost: 4200,
          },
        ]),
      ),
      monthly: vi.fn(() =>
        of([
          {
            month: '2026-09',
            availability: 0.98,
            unplannedDowntimeHours: 30,
            failures: 6,
            mttrHours: 5,
            maintenanceCost: 3000,
            correctiveOrders: 6,
            plannedOrders: 8,
            precision: 0.7,
            recall: 0.6,
          },
          {
            month: '2026-10',
            availability: 0.99,
            unplannedDowntimeHours: 20,
            failures: 4,
            mttrHours: 4,
            maintenanceCost: 2500,
            correctiveOrders: 4,
            plannedOrders: 8,
            precision: 0.8,
            recall: 0.7,
          },
        ]),
      ),
      status: vi.fn(() =>
        of({
          lastLoad: '2026-10-30T14:55:00Z',
          recentRuns: [],
          dataFrom: '2026-05-03',
          simulatedHistory: true,
          factories: ['Bizerte Plant', 'Sfax Plant'],
          powerBiReportUrl,
        }),
      ),
      refresh: vi.fn(() => of({})),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: AnalyticsApi, useValue: api },
        {
          provide: Auth,
          useValue: { hasAnyRole: (...wanted: string[]) => wanted.some((r) => roles.includes(r)) },
        },
      ],
    });
    const fixture = TestBed.createComponent(Analytics);
    fixture.detectChanges();
    return { fixture, api, text: () => (fixture.nativeElement as HTMLElement).textContent ?? '' };
  }

  it('shows the KPIs of the last 90 days', async () => {
    const { fixture, api, text } = render(['OPERATOR']);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.overview).toHaveBeenCalledWith(expect.any(String), expect.any(String), null);
    expect(text()).toContain('98.6%');
    expect(text()).toContain('643 h');
    expect(text()).toContain('6.2 h');
    expect(text()).toContain('75%');
    expect(text()).toContain('includes simulated history');
    expect(text()).toContain('M-102');
  });

  it('offers the load button to admins only and the Power BI link when one is set', async () => {
    const operator = render(['OPERATOR']);
    await operator.fixture.whenStable();
    operator.fixture.detectChanges();
    expect(operator.text()).not.toContain('Load now');
    expect(operator.text()).not.toContain('Open Power BI report');

    TestBed.resetTestingModule();
    const admin = render(['ADMIN'], 'https://app.powerbi.com/groups/me/reports/1');
    await admin.fixture.whenStable();
    admin.fixture.detectChanges();
    expect(admin.text()).toContain('Load now');
    const link = (admin.fixture.nativeElement as HTMLElement).querySelector('a[href*="powerbi"]');
    expect(link).not.toBeNull();
  });
});
