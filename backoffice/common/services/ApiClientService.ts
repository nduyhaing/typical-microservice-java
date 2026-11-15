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
 * Browser: same-origin `/api` through backoffice-bff (pages load data in the browser; no SSR calls today).
 */
export function apiBaseUrl(): string {
  if (typeof window === 'undefined') {
    return process.env.API_BASE_PATH_SERVER ?? 'http://localhost:8088/api';
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

/** What nginx answers (as HTML) when a request body exceeds client_max_body_size (decisions.md 3b-23). */
export const TOO_LARGE_MESSAGE = 'File is too large (max 10 MB)';

async function request<T>(method: string, path: string, payload?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const init: RequestInit = { method, headers };
  if (payload instanceof FormData) {
    // multipart: the browser sets Content-Type with the boundary
    init.body = payload;
  } else if (payload !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(payload);
  }
  const response = await fetch(`${apiBaseUrl()}${path}`, init);
  const body = parseBody(await response.text());
  if (!response.ok) {
    if (response.status === 413 && (body === null || typeof body !== 'object')) {
      throw new ApiError(413, { detail: TOO_LARGE_MESSAGE, fieldErrors: [] });
    }
    throw new ApiError(response.status, body);
  }
  return body as T;
}

export const get = <T>(path: string) => request<T>('GET', path);

export const post = <T>(path: string, payload: unknown) => request<T>('POST', path, payload);

/** Multipart POST (file uploads). */
export const postForm = <T>(path: string, form: FormData) => request<T>('POST', path, form);

export const put = <T>(path: string, payload: unknown) => request<T>('PUT', path, payload);

export const del = <T = null>(path: string) => request<T>('DELETE', path);
