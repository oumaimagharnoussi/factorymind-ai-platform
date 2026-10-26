import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from '../auth/auth';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  /** Roadmap phase for screens that are not built yet. */
  phase?: string;
}

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(Auth);

  protected readonly nav: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: 'space_dashboard' },
    { path: '/factories', label: 'Factories', icon: 'factory' },
    { path: '/assets', label: 'Machines', icon: 'precision_manufacturing' },
    { path: '/monitoring', label: 'Live monitoring', icon: 'monitor_heart', phase: 'Weeks 5–7' },
    { path: '/maintenance/orders', label: 'Work orders', icon: 'build', phase: 'Weeks 8–9' },
    { path: '/inventory', label: 'Spare parts', icon: 'inventory_2', phase: 'Weeks 8–9' },
    { path: '/ai-insights', label: 'AI risk center', icon: 'insights', phase: 'Weeks 10–11' },
    { path: '/copilot', label: 'Maintenance copilot', icon: 'smart_toy', phase: 'Weeks 12–13' },
    { path: '/analytics', label: 'Analytics', icon: 'bar_chart', phase: 'Weeks 12–13' },
  ];
}
