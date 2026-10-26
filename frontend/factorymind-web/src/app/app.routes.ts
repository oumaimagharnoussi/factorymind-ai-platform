import { Routes } from '@angular/router';
import { roleGuard } from './core/auth/role.guard';
import { Shell } from './core/layout/shell';

const soon = (title: string, phase: string, description: string) => ({
  loadComponent: () => import('./features/placeholder/coming-soon').then((m) => m.ComingSoon),
  data: { title, phase, description },
  title: `${title} · FactoryMind AI`,
});

export const routes: Routes = [
  {
    path: '',
    component: Shell,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · FactoryMind AI',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'factories',
        title: 'Factories · FactoryMind AI',
        loadComponent: () => import('./features/factories/factory-list').then((m) => m.FactoryList),
      },
      {
        path: 'assets',
        title: 'Machines · FactoryMind AI',
        loadComponent: () => import('./features/assets/asset-list').then((m) => m.AssetList),
      },
      {
        path: 'assets/new',
        title: 'New machine · FactoryMind AI',
        canActivate: [roleGuard('ADMIN', 'ENGINEER')],
        loadComponent: () => import('./features/assets/asset-form').then((m) => m.AssetForm),
      },
      {
        path: 'assets/:id',
        title: 'Machine · FactoryMind AI',
        loadComponent: () => import('./features/assets/asset-detail').then((m) => m.AssetDetail),
      },
      {
        path: 'monitoring',
        ...soon('Live monitoring', 'weeks 5–7', 'Sensor simulator → Kafka → telemetry service → live charts and threshold alerts.'),
      },
      {
        path: 'maintenance/orders',
        ...soon('Work orders', 'weeks 8–9', 'Maintenance orders from OPEN to IN_PROGRESS to COMPLETED, with parts used and root cause.'),
      },
      {
        path: 'inventory',
        ...soon('Spare parts', 'weeks 8–9', 'Spare parts catalogue, stock levels and stock movements linked to work orders.'),
      },
      {
        path: 'ai-insights',
        ...soon('AI risk center', 'weeks 10–11', 'Anomaly detection and 7-day failure-risk scores from the Python ML service, with explanations.'),
      },
      {
        path: 'copilot',
        ...soon('Maintenance copilot', 'weeks 12–13', 'Spring AI + RAG assistant that answers from machine manuals and cites its sources.'),
      },
      {
        path: 'analytics',
        ...soon('Analytics', 'weeks 12–13', 'Power BI reports: availability, downtime, MTBF/MTTR, maintenance cost and AI accuracy.'),
      },
      { path: '**', redirectTo: 'dashboard' },
    ],
  },
];
