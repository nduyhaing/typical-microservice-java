import { ApiError } from './ApiClientService';

/** What to show for a failed API call. */
export type ErrorInfo = { message: string; fieldErrors: string[] };

const GENERIC = 'Something went wrong. Please try again later.';

/** The product service's ErrorVm `{statusCode, title, detail, fieldErrors}`. */
type ErrorVm = { detail?: unknown; fieldErrors?: unknown };

function asErrorVm(body: unknown): ErrorVm {
  return body !== null && typeof body === 'object' ? (body as ErrorVm) : {};
}

export function toErrorInfo(error: unknown): ErrorInfo {
  if (!(error instanceof ApiError)) {
    return { message: GENERIC, fieldErrors: [] };
  }
  if (error.status === 401) {
    // backoffice-bff answers 401 to /api calls once the session is gone (2b-13); a reload goes through the login
    return { message: 'Your session has expired. Reload the page to log in again.', fieldErrors: [] };
  }
  if (error.status === 403) {
    return { message: 'You are not allowed to do this. The back office is for administrators only.', fieldErrors: [] };
  }
  const { detail, fieldErrors } = asErrorVm(error.body);
  if (error.status >= 400 && error.status < 500 && typeof detail === 'string' && detail) {
    return {
      message: detail,
      fieldErrors: Array.isArray(fieldErrors) ? fieldErrors.filter((e): e is string => typeof e === 'string') : [],
    };
  }
  return { message: GENERIC, fieldErrors: [] };
}
