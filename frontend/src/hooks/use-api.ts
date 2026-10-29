"use client";

import { useCallback, useEffect, useEffectEvent, useState } from "react";

import { errorMessage } from "@/lib/api/client";

interface State<T> {
  /** Which request (deps + reload counter) produced this state. */
  key: string | null;
  data?: T;
  error: string | null;
}

/**
 * Loads data on mount and whenever `deps` change; results of superseded requests are ignored.
 * State is only set from promise callbacks, never synchronously inside the effect.
 */
export function useApi<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [nonce, setNonce] = useState(0);
  const [state, setState] = useState<State<T>>({ key: null, error: null });
  const requestKey = `${JSON.stringify(deps)}#${nonce}`;
  const fetchData = useEffectEvent(fn);

  useEffect(() => {
    let active = true;
    fetchData().then(
      (data) => active && setState({ key: requestKey, data, error: null }),
      (e) => active && setState((s) => ({ key: requestKey, data: s.data, error: errorMessage(e) })),
    );
    return () => {
      active = false;
    };
  }, [requestKey]);

  const reload = useCallback(() => setNonce((n) => n + 1), []);
  const setData = useCallback((data: T) => setState((s) => ({ ...s, data, error: null })), []);
  const loading = state.key !== requestKey;

  return { data: state.data, error: loading ? null : state.error, loading, reload, setData };
}
