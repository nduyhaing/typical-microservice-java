/** A new webhook secret: 32 random bytes from the browser's CSPRNG, as 64 hex characters. */
export function generateSecret(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}
