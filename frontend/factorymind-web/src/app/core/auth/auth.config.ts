import { KeycloakConfig } from 'keycloak-js';

/**
 * Keycloak serves under /auth. Locally (ng serve or Docker) it listens on port 8180;
 * in production Caddy serves it on the app's own domain.
 */
export const keycloakConfig: KeycloakConfig = {
  url: location.hostname === 'localhost' ? 'http://localhost:8180/auth' : `${location.origin}/auth`,
  realm: 'factorymind',
  clientId: 'factorymind-web',
};
