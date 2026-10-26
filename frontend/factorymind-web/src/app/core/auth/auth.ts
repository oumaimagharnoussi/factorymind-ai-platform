import { Injectable, computed, signal } from '@angular/core';
import Keycloak, { KeycloakConfig } from 'keycloak-js';
import { keycloakConfig } from './auth.config';

export const ROLES = ['ADMIN', 'ENGINEER', 'OPERATOR'] as const;
export type Role = (typeof ROLES)[number];

export interface CurrentUser {
  username: string;
  name: string;
  roles: Role[];
}

type AuthState = 'loading' | 'authenticated' | 'unavailable';

/** Keycloak login (authorization code + PKCE) and the signed-in user's roles. */
@Injectable({ providedIn: 'root' })
export class Auth {
  private keycloak?: Keycloak;
  private readonly currentUser = signal<CurrentUser | null>(null);

  readonly state = signal<AuthState>('loading');
  readonly user = this.currentUser.asReadonly();
  readonly roles = computed(() => this.currentUser()?.roles ?? []);

  /** Runs before the first route: sends the browser to the Keycloak login page if there is no session. */
  async init(config: KeycloakConfig = keycloakConfig): Promise<void> {
    this.keycloak = new Keycloak(config);
    try {
      await this.keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false });
      this.currentUser.set(this.readUser());
      this.state.set('authenticated');
    } catch (error) {
      console.error('Keycloak is not reachable', error);
      this.state.set('unavailable');
    }
  }

  hasAnyRole(...roles: Role[]): boolean {
    return this.roles().some((role) => roles.includes(role));
  }

  /** A token valid for at least 30 more seconds, refreshed when needed. */
  async token(): Promise<string | undefined> {
    if (!this.keycloak?.authenticated) return undefined;
    try {
      await this.keycloak.updateToken(30);
    } catch {
      // The Keycloak session itself is over: start a new login.
      await this.keycloak.login();
    }
    return this.keycloak.token;
  }

  logout(): void {
    void this.keycloak?.logout({ redirectUri: location.origin });
  }

  private readUser(): CurrentUser {
    const token = this.keycloak?.tokenParsed ?? {};
    const realmRoles = token.realm_access?.roles ?? [];
    return {
      username: token['preferred_username'] ?? '',
      name: token['name'] ?? token['preferred_username'] ?? '',
      roles: ROLES.filter((role) => realmRoles.includes(role)),
    };
  }
}
