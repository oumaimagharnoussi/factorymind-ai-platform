import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, defer } from 'rxjs';
import {
  AppNotification,
  Delivery,
  DeliveryStatus,
  NotificationChannel,
} from '../../core/api/models';
import { NotificationApi } from '../../core/api/notification-api';
import { stayConnected } from '../../core/api/sse';
import { Auth } from '../../core/auth/auth';
import {
  CHANNEL_ICONS,
  CHANNEL_LABELS,
  SOURCE_ICONS,
  SOURCE_LABELS,
  isExternal,
} from '../../shared/notification-labels';
import { StatusChip } from '../../shared/status-chip';

/**
 * Everything notification-service told the signed-in user (by role), and for engineers and admins the
 * delivery log: every e-mail and Teams message, with retries, failures and throttled duplicates.
 */
@Component({
  selector: 'app-notification-center',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTabsModule,
    MatTooltipModule,
    StatusChip,
  ],
  templateUrl: './notification-center.html',
  styleUrl: './notifications.scss',
})
export class NotificationCenter {
  private readonly api = inject(NotificationApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(Auth);

  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly sourceIcons = SOURCE_ICONS;
  protected readonly channelLabels = CHANNEL_LABELS;
  protected readonly channelIcons = CHANNEL_ICONS;
  protected readonly isExternal = isExternal;
  protected readonly canSeeDeliveries = this.auth.hasAnyRole('ADMIN', 'ENGINEER');
  protected readonly isAdmin = this.auth.hasAnyRole('ADMIN');

  protected readonly unreadOnly = signal(false);
  protected readonly page = signal({ index: 0, size: 20 });
  protected readonly feed = rxResource({
    params: () => ({ unreadOnly: this.unreadOnly(), ...this.page() }),
    stream: ({ params }) => this.api.feed(params.unreadOnly, params.index, params.size),
  });

  protected readonly deliveryColumns = [
    'status',
    'channel',
    'event',
    'rule',
    'attempts',
    'created',
    'actions',
  ];
  protected readonly deliveryStatus = signal<DeliveryStatus | null>(null);
  protected readonly deliveryChannel = signal<NotificationChannel | null>(null);
  protected readonly deliveryPage = signal({ index: 0, size: 20 });
  protected readonly deliveries = rxResource({
    params: () =>
      this.canSeeDeliveries
        ? {
            status: this.deliveryStatus(),
            channel: this.deliveryChannel(),
            ...this.deliveryPage(),
          }
        : undefined,
    stream: ({ params }) =>
      this.api.deliveries(params.status, params.channel, params.index, params.size),
  });

  constructor() {
    defer(() => this.api.stream())
      .pipe(stayConnected(), debounceTime(500), takeUntilDestroyed())
      .subscribe(() => {
        this.feed.reload();
        this.deliveries.reload();
      });
  }

  protected setUnreadOnly(value: boolean): void {
    this.unreadOnly.set(value);
    this.page.update((p) => ({ ...p, index: 0 }));
  }

  protected onPage(event: PageEvent): void {
    this.page.set({ index: event.pageIndex, size: event.pageSize });
  }

  protected open(n: AppNotification): void {
    if (!n.read) {
      this.api.markRead(n.id).subscribe(() => this.feed.reload());
    }
    if (!n.link) return;
    if (isExternal(n.link)) {
      window.open(n.link, '_blank', 'noopener');
    } else {
      void this.router.navigateByUrl(n.link);
    }
  }

  protected markRead(n: AppNotification, event: Event): void {
    event.stopPropagation();
    this.api.markRead(n.id).subscribe(() => this.feed.reload());
  }

  protected markAllRead(): void {
    this.api.markAllRead().subscribe(() => this.feed.reload());
  }

  protected setDeliveryStatus(status: DeliveryStatus | null): void {
    this.deliveryStatus.set(status);
    this.deliveryPage.update((p) => ({ ...p, index: 0 }));
  }

  protected setDeliveryChannel(channel: NotificationChannel | null): void {
    this.deliveryChannel.set(channel);
    this.deliveryPage.update((p) => ({ ...p, index: 0 }));
  }

  protected onDeliveryPage(event: PageEvent): void {
    this.deliveryPage.set({ index: event.pageIndex, size: event.pageSize });
  }

  protected canRetry(d: Delivery): boolean {
    return this.isAdmin && (d.status === 'FAILED' || d.status === 'SUPPRESSED');
  }

  protected retry(d: Delivery): void {
    this.api.retryDelivery(d.id).subscribe(() => this.deliveries.reload());
  }
}
