/** A non-2xx API response. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: unknown
  ) {
    super(`API request failed with status ${status}`);
    this.name = 'ApiError';
  }
}

/**
 * Browser: same-origin `/api` through storefront-bff. Server (SSR): the BFF on the compose network.
 */
export function apiBaseUrl(): string {
  if (typeof window === 'undefined') {
    return process.env.API_BASE_PATH_SERVER ?? 'http://localhost:8087/api';
  }
  return '/api';
}

export async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${apiBaseUrl()}${path}`, { headers: { Accept: 'application/json' } });
  const text = await response.text();
  const body: unknown = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new ApiError(response.status, body);
  }
  return body as T;
}
