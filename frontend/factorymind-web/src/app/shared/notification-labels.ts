import { NotificationChannel, NotificationSource } from '../core/api/models';

/** Keyed by string so table rows (typed any) can index it; every NotificationSource must be present. */
export const SOURCE_LABELS: Readonly<Record<string, string>> = {
  ALERT: 'Sensor alerts',
  WORK_ORDER: 'Work orders',
  AI_RISK: 'AI failure risk',
  PLATFORM: 'Platform monitoring',
} satisfies Record<NotificationSource, string>;

/** Keyed by string so table rows (typed any) can index it; every NotificationSource must be present. */
export const SOURCE_ICONS: Readonly<Record<string, string>> = {
  ALERT: 'notification_important',
  WORK_ORDER: 'build',
  AI_RISK: 'insights',
  PLATFORM: 'dns',
} satisfies Record<NotificationSource, string>;

/** Keyed by string so table rows (typed any) can index it; every NotificationChannel must be present. */
export const CHANNEL_LABELS: Readonly<Record<string, string>> = {
  EMAIL: 'E-mail',
  TEAMS: 'Microsoft Teams',
  IN_APP: 'In the app',
} satisfies Record<NotificationChannel, string>;

/** Keyed by string so table rows (typed any) can index it; every NotificationChannel must be present. */
export const CHANNEL_ICONS: Readonly<Record<string, string>> = {
  EMAIL: 'mail',
  TEAMS: 'groups',
  IN_APP: 'notifications',
} satisfies Record<NotificationChannel, string>;

/** Links are app paths ("/alerts") or full URLs to monitoring tools, which open in a new tab. */
export function isExternal(link: string | null): boolean {
  return !!link && /^https?:\/\//.test(link);
}
