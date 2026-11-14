import { renderHook, waitFor } from '@testing-library/react';
import { useFetch } from './useFetch';

describe('useFetch', () => {
  it('starts loading, then exposes the data', async () => {
    const { result } = renderHook(() => useFetch(() => Promise.resolve(['phones'])));

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual(['phones']);
    expect(result.current.error).toBeUndefined();
  });

  it('error state: exposes the error and stops loading', async () => {
    const { result } = renderHook(() => useFetch(() => Promise.reject(new Error('down'))));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error?.message).toBe('down');
    expect(result.current.data).toBeUndefined();
  });
});
