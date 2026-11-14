import { useRouter } from 'next/router';

/** The numeric `[id]` route parameter, or undefined until the router is ready (or when it is not a number). */
export function useRecordId(): number | undefined {
  const router = useRouter();
  const id = Number(router.query.id);
  return router.isReady && Number.isInteger(id) && id > 0 ? id : undefined;
}

/** A promise that never settles: keeps `useFetch` loading until the route parameter is known. */
export const pending = <T>() => new Promise<T>(() => undefined);
