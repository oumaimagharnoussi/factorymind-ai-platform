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
        title: 'Live monitoring · FactoryMind AI',
        loadComponent: () => import('./features/monitoring/live-monitoring').then((m) => m.LiveMonitoring),
      },
      {
        path: 'alerts',
        title: 'Alerts · FactoryMind AI',
        loadComponent: () => import('./features/alerts/alert-list').then((m) => m.AlertList),
      },
      {
        path: 'maintenance/orders',
        title: 'Work orders · FactoryMind AI',
        loadComponent: () => import('./features/maintenance/work-order-list').then((m) => m.WorkOrderList),
      },
      {
        path: 'maintenance/orders/new',
        title: 'New work order · FactoryMind AI',
        loadComponent: () => import('./features/maintenance/work-order-form').then((m) => m.WorkOrderForm),
      },
      {
        path: 'maintenance/orders/:id',
        title: 'Work order · FactoryMind AI',
        loadComponent: () => import('./features/maintenance/work-order-detail').then((m) => m.WorkOrderDetail),
      },
      {
        path: 'inventory',
        title: 'Spare parts · FactoryMind AI',
        loadComponent: () => import('./features/inventory/part-list').then((m) => m.PartList),
      },
      {
        path: 'inventory/:id',
        title: 'Spare part · FactoryMind AI',
        loadComponent: () => import('./features/inventory/part-detail').then((m) => m.PartDetail),
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
