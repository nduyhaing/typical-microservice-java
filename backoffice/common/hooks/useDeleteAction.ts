import { useState } from 'react';

/**
 * Delete with a browser confirmation first; remembers the outcome (a notice, or the error such as the service's
 * 400 "brand still has products") and calls `onDeleted` after a successful delete.
 */
export function useDeleteAction(kind: string, onDeleted: () => void) {
  const [error, setError] = useState<unknown>();
  const [notice, setNotice] = useState<string>();

  async function remove(name: string, action: () => Promise<unknown>) {
    if (!window.confirm(`Delete ${kind} "${name}"?`)) {
      return;
    }
    setError(undefined);
    setNotice(undefined);
    try {
      await action();
      setNotice(`${kind.charAt(0).toUpperCase()}${kind.slice(1)} "${name}" deleted.`);
      onDeleted();
    } catch (e) {
      setError(e);
    }
  }

  return { remove, error, notice };
}
