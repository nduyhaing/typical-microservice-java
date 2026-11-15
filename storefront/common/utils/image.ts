/** Shown for products without an image. */
export const PLACEHOLDER_IMAGE = '/static/images/default.png';

export function imageOrPlaceholder(url?: string | null): string {
  return url ? url : PLACEHOLDER_IMAGE;
}
