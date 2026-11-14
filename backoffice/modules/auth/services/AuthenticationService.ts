import { AuthenticationInfo } from '../models/AuthenticationInfo';

/** Logout is a POST to backoffice-bff, which also ends the Keycloak session. */
export const LOGOUT_URL = '/logout';

const ANONYMOUS: AuthenticationInfo = { isAuthenticated: false, authenticatedUser: null };

/** Asks backoffice-bff (same origin) who is logged in; the BFF only lets admins reach this app at all. */
export async function getAuthentication(): Promise<AuthenticationInfo> {
  try {
    const response = await fetch('/authentication', { headers: { Accept: 'application/json' } });
    return response.ok ? ((await response.json()) as AuthenticationInfo) : ANONYMOUS;
  } catch {
    return ANONYMOUS;
  }
}
