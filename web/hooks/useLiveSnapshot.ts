"use client";

import { useEffect, useRef, useState } from "react";
import { mapSnapshot } from "@/lib/api";
import type { LiveSnapshot } from "@/lib/types";

type ConnectionState = "live" | "reconnecting";

export function useLiveSnapshot(token: string, initialSnapshot: LiveSnapshot) {
  const [snapshot, setSnapshot] = useState<LiveSnapshot>(initialSnapshot);
  const [connectionState, setConnectionState] = useState<ConnectionState>("live");
  const lastMessageAt = useRef(0);

  useEffect(() => {
    if (initialSnapshot.status === "ENDED") return;

    lastMessageAt.current = Date.now();
    const source = new EventSource(`${process.env.NEXT_PUBLIC_API_BASE_URL}/api/v1/live/${token}/stream`);

    source.onmessage = (event) => {
      lastMessageAt.current = Date.now();
      setConnectionState("live");
      const raw = JSON.parse(event.data);
      const next = mapSnapshot(raw);
      setSnapshot(next);
      if (next.status === "ENDED") source.close();
    };

    const staleCheck = setInterval(() => {
      if (Date.now() - lastMessageAt.current > 15_000) setConnectionState("reconnecting");
    }, 5_000);

    return () => {
      source.close();
      clearInterval(staleCheck);
    };
  }, [token, initialSnapshot.status]);

  return { snapshot, connectionState };
}
