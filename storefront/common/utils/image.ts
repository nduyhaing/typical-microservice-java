/** Shown until the media service (slice 3) provides product images. */
export const PLACEHOLDER_IMAGE = '/static/images/default.png';

export function imageOrPlaceholder(url?: string | null): string {
  return url ? url : PLACEHOLDER_IMAGE;
}
