import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import {
  NotificationChannel,
  NotificationSeverity,
  NotificationSource,
  RoutingRule,
  RoutingRuleRequest,
  RuleTestResult,
} from '../../core/api/models';
import { NotificationApi } from '../../core/api/notification-api';
import { Auth } from '../../core/auth/auth';
import { describeError } from '../../core/http/problem';
import { CHANNEL_ICONS, CHANNEL_LABELS, SOURCE_LABELS } from '../../shared/notification-labels';
import { StatusChip } from '../../shared/status-chip';

const RECIPIENT_HINTS: Record<NotificationChannel, string> = {
  EMAIL: 'One or more addresses, separated by commas',
  TEAMS: 'Webhook URL of a Teams channel (Workflows > "When a Teams webhook request is received")',
  IN_APP: 'The role whose bell rings: ADMIN, ENGINEER or OPERATOR',
};

function emptyDraft(): RoutingRuleRequest {
  return {
    name: '',
    source: null,
    minSeverity: 'CRITICAL',
    channel: 'EMAIL',
    recipient: '',
    throttleMinutes: 10,
    enabled: true,
  };
}

/**
 * Who hears about what: each rule sends the events of a source, at or above a severity, over one channel.
 * Engineers read the rules; admins change them and send test messages.
 */
@Component({
  selector: 'app-routing-rules',
  imports: [
    DatePipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTableModule,
    MatTooltipModule,
    StatusChip,
  ],
  templateUrl: './routing-rules.html',
  styleUrl: './notifications.scss',
})
export class RoutingRules {
  private readonly api = inject(NotificationApi);
  protected readonly isAdmin = inject(Auth).hasAnyRole('ADMIN');

  protected readonly sources = Object.keys(SOURCE_LABELS) as NotificationSource[];
  protected readonly severities: NotificationSeverity[] = ['INFO', 'WARNING', 'CRITICAL'];
  protected readonly channels: NotificationChannel[] = ['EMAIL', 'TEAMS', 'IN_APP'];
  protected readonly roles = ['OPERATOR', 'ENGINEER', 'ADMIN'];
  protected readonly sourceLabels = SOURCE_LABELS;
  protected readonly channelLabels = CHANNEL_LABELS;
  protected readonly channelIcons = CHANNEL_ICONS;
  protected readonly recipientHints = RECIPIENT_HINTS;
  protected readonly columns = [
    'enabled',
    'name',
    'when',
    'channel',
    'throttle',
    'updated',
    'actions',
  ];

  protected readonly rules = rxResource({ stream: () => this.api.rules() });

  /** The rule being created (id null) or edited, if the form is open. */
  protected readonly editing = signal<{ id: string | null; draft: RoutingRuleRequest } | null>(
    null,
  );
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly testResult = signal<(RuleTestResult & { rule: string }) | null>(null);

  protected create(): void {
    this.error.set(null);
    this.editing.set({ id: null, draft: emptyDraft() });
  }

  protected edit(rule: RoutingRule): void {
    this.error.set(null);
    this.editing.set({
      id: rule.id,
      draft: {
        name: rule.name,
        source: rule.source,
        minSeverity: rule.minSeverity,
        channel: rule.channel,
        recipient: rule.recipient,
        throttleMinutes: rule.throttleMinutes,
        enabled: rule.enabled,
        version: rule.version,
      },
    });
  }

  protected channelChanged(draft: RoutingRuleRequest): void {
    draft.recipient = draft.channel === 'IN_APP' ? 'OPERATOR' : '';
    draft.throttleMinutes = draft.channel === 'IN_APP' ? 0 : 10;
  }

  protected save(): void {
    const form = this.editing();
    if (!form) return;
    this.saving.set(true);
    this.error.set(null);
    const request = form.id
      ? this.api.updateRule(form.id, form.draft)
      : this.api.createRule(form.draft);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(null);
        this.rules.reload();
      },
      error: (e) => {
        this.saving.set(false);
        this.error.set(describeError(e));
      },
    });
  }

  protected toggle(rule: RoutingRule): void {
    const { id, updatedAt, updatedBy, ...rest } = rule;
    void updatedAt;
    void updatedBy;
    this.api
      .updateRule(id, { ...rest, enabled: !rule.enabled })
      .subscribe(() => this.rules.reload());
  }

  protected remove(rule: RoutingRule): void {
    if (!confirm(`Delete the rule "${rule.name}"? Its delivery history is kept.`)) return;
    this.api.deleteRule(rule.id).subscribe(() => this.rules.reload());
  }

  protected test(rule: RoutingRule): void {
    this.testResult.set(null);
    this.api
      .testRule(rule.id)
      .subscribe((result) => this.testResult.set({ ...result, rule: rule.name }));
  }
}
