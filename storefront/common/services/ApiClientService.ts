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

/** JSON when the body is JSON; otherwise the raw text (error pages from proxies or Tomcat are HTML). */
function parseBody(text: string): unknown {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function get<T>(path: string): Promise<T> {
  const response = await fetch(`${apiBaseUrl()}${path}`, { headers: { Accept: 'application/json' } });
  const body = parseBody(await response.text());
  if (!response.ok) {
    throw new ApiError(response.status, body);
  }
  return body as T;
}
