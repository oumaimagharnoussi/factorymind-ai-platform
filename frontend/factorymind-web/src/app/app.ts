import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterOutlet } from '@angular/router';
import { Auth } from './core/auth/auth';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, MatButtonModule, MatIconModule],
  template: `
    @if (auth.state() === 'unavailable') {
      <div class="auth-down" role="alert">
        <mat-icon>lock</mat-icon>
        <h1>Sign-in service unreachable</h1>
        <p>FactoryMind could not contact Keycloak. Start it with <code>docker compose up -d keycloak</code>, then reload.</p>
        <button mat-flat-button (click)="reload()">Reload</button>
      </div>
    } @else {
      <router-outlet />
    }
  `,
  styles: `
    .auth-down {
      max-width: 480px;
      margin: 15vh auto;
      padding: 0 16px;
      text-align: center;
    }
    .auth-down mat-icon {
      color: var(--fm-warn);
    }
  `,
})
export class App {
  protected readonly auth = inject(Auth);

  protected reload(): void {
    location.reload();
  }
}
