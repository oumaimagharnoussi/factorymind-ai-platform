import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { defer } from 'rxjs';
import { AppNotification } from '../api/models';
import { NotificationApi } from '../api/notification-api';
import { stayConnected } from '../api/sse';
import { SOURCE_ICONS, isExternal } from '../../shared/notification-labels';

const RECENT = 8;

/**
 * The bell in the toolbar: unread count, the latest notifications, and a toast for critical ones. New
 * notifications arrive on the live stream, so the count changes without polling.
 */
@Component({
  selector: 'app-notification-bell',
  imports: [
    DatePipe,
    RouterLink,
    MatBadgeModule,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatMenuModule,
    MatTooltipModule,
  ],
  template: `
    <button
      mat-icon-button
      class="bell"
      [matMenuTriggerFor]="menu"
      (menuOpened)="loadRecent()"
      [matTooltip]="live() ? 'Notifications' : 'Notifications (reconnecting)'"
      aria-label="Notifications"
    >
      <mat-icon
        [matBadge]="unread() > 99 ? '99+' : unread()"
        [matBadgeHidden]="unread() === 0"
        matBadgeColor="warn"
        matBadgeSize="small"
        aria-hidden="false"
        >{{ unread() ? 'notifications_active' : 'notifications_none' }}</mat-icon
      >
    </button>
    <mat-menu #menu="matMenu" xPosition="before" class="notification-menu">
      <div class="menu-header" (click)="$event.stopPropagation()">
        <strong>Notifications</strong>
        @if (unread()) {
          <button mat-button (click)="markAllRead()">Mark all as read</button>
        }
      </div>
      <mat-divider />
      @for (n of recent(); track n.id) {
        <button mat-menu-item class="item" [class.unread]="!n.read" (click)="open(n)">
          <mat-icon [class]="'sev ' + n.severity.toLowerCase()">{{ icons[n.source] }}</mat-icon>
          <span class="text">
            <span class="title">{{ n.title }}</span>
            <span class="when">{{ n.createdAt | date: 'MMM d, HH:mm' }}</span>
          </span>
        </button>
      } @empty {
        <p class="empty">
          Nothing yet. Alerts, work orders and AI warnings for your role show up here.
        </p>
      }
      <mat-divider />
      <a mat-menu-item routerLink="/notifications">
        <mat-icon>list</mat-icon>
        See all notifications
      </a>
    </mat-menu>
  `,
  styles: `
    .menu-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 4px 8px 4px 16px;
      min-width: 320px;
    }
    .item {
      height: auto;
      min-height: 56px;
      line-height: 1.3;
      white-space: normal;
    }
    .item.unread .title {
      font-weight: 600;
    }
    .text {
      display: flex;
      flex-direction: column;
      max-width: 340px;
    }
    .when {
      font-size: 12px;
      color: var(--fm-text-muted);
    }
    .sev.critical {
      color: var(--fm-bad);
    }
    .sev.warning {
      color: var(--fm-warn);
    }
    .sev.info {
      color: var(--fm-info);
    }
    .empty {
      padding: 12px 16px;
      max-width: 320px;
      color: var(--fm-text-muted);
      font-size: 13px;
    }
  `,
})
export class NotificationBell {
  private readonly api = inject(NotificationApi);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly icons = SOURCE_ICONS;
  protected readonly unread = signal(0);
  protected readonly recent = signal<AppNotification[]>([]);
  protected readonly live = signal(false);

  constructor() {
    this.api.unreadCount().subscribe((count) => this.unread.set(count));
    defer(() => this.api.stream(() => this.live.set(true)))
      .pipe(
        stayConnected(() => this.live.set(false)),
        takeUntilDestroyed(),
      )
      .subscribe((n) => this.onNew(n));
  }

  protected loadRecent(): void {
    this.api.feed(false, 0, RECENT).subscribe((page) => this.recent.set(page.content));
  }

  protected open(n: AppNotification): void {
    if (!n.read) {
      this.api.markRead(n.id).subscribe();
      this.unread.update((count) => Math.max(0, count - 1));
      this.recent.update((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
    }
    this.follow(n.link);
  }

  protected markAllRead(): void {
    this.api.markAllRead().subscribe(() => {
      this.unread.set(0);
      this.recent.update((list) => list.map((x) => ({ ...x, read: true })));
    });
  }

  private onNew(n: AppNotification): void {
    this.unread.update((count) => count + 1);
    this.recent.update((list) => [n, ...list.filter((x) => x.id !== n.id)].slice(0, RECENT));
    if (n.severity === 'CRITICAL') {
      this.snackBar
        .open(n.title, 'Open', { duration: 8000, panelClass: 'critical-toast' })
        .onAction()
        .subscribe(() => this.open(n));
    }
  }

  private follow(link: string | null): void {
    if (!link) return;
    if (isExternal(link)) {
      window.open(link, '_blank', 'noopener');
    } else {
      void this.router.navigateByUrl(link);
    }
  }
}
