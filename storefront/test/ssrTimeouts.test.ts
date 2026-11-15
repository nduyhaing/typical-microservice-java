import { GetServerSidePropsContext } from 'next';
import { afterEach, vi } from 'vitest';
import { getAverageStar, getRatingsByProduct, RATING_TIMEOUT_MS } from '../modules/rating/services/RatingService';
import { SEARCH_TIMEOUT_MS } from '../modules/search/services/SearchService';
import { getServerSideProps as searchPageProps } from '../pages/search';

/** A service that accepts the connection and never answers; it only gives up when the request is aborted. */
const hangingFetch = vi.fn(
  (_url: string, init?: RequestInit) =>
    new Promise<Response>((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(init.signal?.reason));
    })
);

const NEVER = Symbol('never settled');

/** Settles with the promise's outcome, or NEVER when it is still pending after `ms`. */
async function within<T>(promise: Promise<T>, ms: number): Promise<T | typeof NEVER | unknown> {
  return Promise.race([
    promise.catch((error: unknown) => error),
    new Promise((resolve) => setTimeout(() => resolve(NEVER), ms)),
  ]);
}

afterEach(() => {
  vi.unstubAllGlobals();
  hangingFetch.mockClear();
});

describe('SSR calls to optional services are bounded (slice 4 review I-6)', () => {
  it('rating calls give up after RATING_TIMEOUT_MS', async () => {
    vi.stubGlobal('fetch', hangingFetch);
    expect(RATING_TIMEOUT_MS).toBe(1500);

    const started = Date.now();
    const [average, ratings] = await Promise.all([
      within(getAverageStar(2), RATING_TIMEOUT_MS + 2000),
      within(getRatingsByProduct(2, 0, 5), RATING_TIMEOUT_MS + 2000),
    ]);

    expect(average).not.toBe(NEVER);
    expect(ratings).not.toBe(NEVER);
    expect((average as Error).name).toBe('TimeoutError');
    expect(Date.now() - started).toBeLessThan(RATING_TIMEOUT_MS + 1000);
  }, 10_000);

  it('a search that never answers renders the page without results ("not available")', async () => {
    vi.stubGlobal('fetch', hangingFetch);
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    const result = await within(
      searchPageProps({ query: { keyword: 'galaxy' } } as unknown as GetServerSidePropsContext),
      SEARCH_TIMEOUT_MS + 2000
    );

    expect(result).not.toBe(NEVER);
    expect(result).toMatchObject({ props: { result: null } });
  }, 10_000);
});
