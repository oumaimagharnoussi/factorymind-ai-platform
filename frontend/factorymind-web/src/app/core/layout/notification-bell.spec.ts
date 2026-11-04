import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { AppNotification } from '../api/models';
import { NotificationApi } from '../api/notification-api';
import { NotificationBell } from './notification-bell';

function notification(severity: AppNotification['severity'], title: string): AppNotification {
  return {
    id: crypto.randomUUID(),
    source: 'ALERT',
    severity,
    assetId: null,
    assetLabel: 'M-102 Hydraulic press',
    title,
    message: '',
    link: '/alerts',
    occurredAt: '2026-11-04T08:00:00Z',
    createdAt: '2026-11-04T08:00:00Z',
    read: false,
  };
}

describe('NotificationBell', () => {
  function render() {
    const live = new Subject<AppNotification>();
    const toasts: string[] = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: NotificationApi,
          useValue: {
            unreadCount: () => of(3),
            stream: () => live,
            feed: () => of({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 }),
            markRead: () => of(undefined),
            markAllRead: () => of({}),
          },
        },
        {
          provide: MatSnackBar,
          useValue: {
            open: (message: string) => {
              toasts.push(message);
              return { onAction: () => new Subject<void>() };
            },
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(NotificationBell);
    fixture.detectChanges();
    return { fixture, live, toasts };
  }

  it('shows the unread count and counts new notifications as they arrive', () => {
    const { fixture, live } = render();
    const badge = () =>
      (fixture.nativeElement as HTMLElement)
        .querySelector('.mat-badge-content')
        ?.textContent?.trim();
    expect(badge()).toBe('3');

    live.next(notification('WARNING', 'WARNING alert on M-102'));
    fixture.detectChanges();
    expect(badge()).toBe('4');
  });

  it('pops a toast for critical notifications only', () => {
    const { fixture, live, toasts } = render();
    live.next(notification('INFO', 'Work order completed'));
    live.next(notification('CRITICAL', 'CRITICAL alert on M-102'));
    fixture.detectChanges();
    expect(toasts).toEqual(['CRITICAL alert on M-102']);
  });
});
