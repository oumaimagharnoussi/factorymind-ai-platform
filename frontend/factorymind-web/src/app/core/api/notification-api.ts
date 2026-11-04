import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, filter, map } from 'rxjs';
import { Auth } from '../auth/auth';
import {
  AppNotification,
  Delivery,
  DeliveryStatus,
  NotificationChannel,
  Page,
  RoutingRule,
  RoutingRuleRequest,
  RuleTestResult,
} from './models';
import { eventStream } from './sse';

/** notification-service: the signed-in user's feed, the routing rules and the delivery log. */
@Injectable({ providedIn: 'root' })
export class NotificationApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(Auth);

  /** Notifications for the user's roles, newest first. */
  feed(unreadOnly = false, page = 0, size = 20): Observable<Page<AppNotification>> {
    const params = new HttpParams()
      .set('unreadOnly', unreadOnly)
      .set('page', page)
      .set('size', size);
    return this.http.get<Page<AppNotification>>('/api/notifications', { params });
  }

  unreadCount(): Observable<number> {
    return this.http
      .get<{ unread: number }>('/api/notifications/unread-count')
      .pipe(map((r) => r.unread));
  }

  markRead(id: string): Observable<void> {
    return this.http.post<void>(`/api/notifications/${id}/read`, null);
  }

  markAllRead(): Observable<unknown> {
    return this.http.post('/api/notifications/read-all', null);
  }

  /** New notifications for the user's roles, as they are routed. Completes every 15 minutes; resubscribe. */
  stream(onOpen?: () => void): Observable<AppNotification> {
    return eventStream('/api/notifications/stream', () => this.auth.token(), onOpen).pipe(
      filter((message) => message.event === 'notification'),
      map((message) => JSON.parse(message.data) as AppNotification),
    );
  }

  rules(): Observable<RoutingRule[]> {
    return this.http.get<RoutingRule[]>('/api/notifications/rules');
  }

  createRule(rule: RoutingRuleRequest): Observable<RoutingRule> {
    return this.http.post<RoutingRule>('/api/notifications/rules', rule);
  }

  updateRule(id: string, rule: RoutingRuleRequest): Observable<RoutingRule> {
    return this.http.put<RoutingRule>(`/api/notifications/rules/${id}`, rule);
  }

  deleteRule(id: string): Observable<void> {
    return this.http.delete<void>(`/api/notifications/rules/${id}`);
  }

  testRule(id: string): Observable<RuleTestResult> {
    return this.http.post<RuleTestResult>(`/api/notifications/rules/${id}/test`, null);
  }

  deliveries(
    status: DeliveryStatus | null,
    channel: NotificationChannel | null,
    page = 0,
    size = 20,
  ): Observable<Page<Delivery>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (channel) params = params.set('channel', channel);
    return this.http.get<Page<Delivery>>('/api/notifications/deliveries', { params });
  }

  retryDelivery(id: string): Observable<Delivery> {
    return this.http.post<Delivery>(`/api/notifications/deliveries/${id}/retry`, null);
  }
}
