const pad = (n: number) => String(n).padStart(2, '0');

/** An ISO instant as the value of a `datetime-local` input (local time, minutes). */
export function toLocalInput(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return '';
  }
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** A `datetime-local` value (local time) as an ISO instant; empty or invalid gives ''. */
export function fromLocalInput(local: string): string {
  if (!local) {
    return '';
  }
  const d = new Date(local);
  return Number.isNaN(d.getTime()) ? '' : d.toISOString();
}
