import { DependencyList, useEffect, useState } from 'react';

export type FetchState<T> = {
  data?: T;
  error?: Error;
  loading: boolean;
};

type Settled<T> = { key: string; data?: T; error?: Error };

/**
 * Runs `fetcher` after render (browser-side data loading, as YAS's catalog pages do) and again whenever `deps`
 * change. State is only set when a request settles; `loading` means "no result yet for the current deps".
 */
export function useFetch<T>(fetcher: () => Promise<T>, deps: DependencyList = []): FetchState<T> {
  const key = JSON.stringify(deps);
  const [settled, setSettled] = useState<Settled<T>>();

  useEffect(() => {
    let cancelled = false;
    fetcher()
      .then((data) => !cancelled && setSettled({ key, data }))
      .catch((error: unknown) => {
        if (!cancelled) {
          setSettled({ key, error: error instanceof Error ? error : new Error(String(error)) });
        }
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `key` captures the caller's deps that drive `fetcher`
  }, [key]);

  if (!settled || settled.key !== key) {
    return { loading: true };
  }
  return { data: settled.data, error: settled.error, loading: false };
}
