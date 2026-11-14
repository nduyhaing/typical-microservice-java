import { AuthenticationInfo } from '../models/AuthenticationInfo';

/** storefront-bff's Keycloak login entry point (a server route, not a Next.js page). */
export const LOGIN_URL = '/oauth2/authorization/keycloak';

/** Logout is a POST to the BFF, which also ends the Keycloak session. */
export const LOGOUT_URL = '/logout';

/** Sends the browser to the Keycloak login (through the BFF). */
export function redirectToLogin(): void {
  // LOGIN_URL is a server route on the BFF (OAuth2 redirect), not a Next.js page: a full navigation is required
  // eslint-disable-next-line @next/next/no-location-assign-relative-destination
  window.location.assign(LOGIN_URL);
}

const ANONYMOUS: AuthenticationInfo = { isAuthenticated: false, authenticatedUser: null };

/** Asks the BFF (same origin, no tokens involved) whether this browser session is logged in. */
export async function getAuthentication(): Promise<AuthenticationInfo> {
  try {
    const response = await fetch('/authentication', { headers: { Accept: 'application/json' } });
    return response.ok ? ((await response.json()) as AuthenticationInfo) : ANONYMOUS;
  } catch {
    return ANONYMOUS;
  }
}
