/** Read-only star display, e.g. ★★★★☆ for 4 (a fractional value is rounded). */
export default function Stars({ value }: { value: number }) {
  const full = Math.max(0, Math.min(5, Math.round(value)));
  return (
    <span className="text-warning" role="img" aria-label={`${full} out of 5 stars`}>
      {'★'.repeat(full)}
      <span className="text-secondary">{'☆'.repeat(5 - full)}</span>
    </span>
  );
}
