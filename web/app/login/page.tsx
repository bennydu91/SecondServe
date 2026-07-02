"use client";

import Script from "next/script";
import { useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import styles from "./page.module.css";

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string;
            callback: (response: { credential: string }) => void;
          }) => void;
          renderButton: (parent: HTMLElement, options: Record<string, unknown>) => void;
        };
      };
    };
  }
}

export default function LoginPage() {
  const router = useRouter();
  const buttonRef = useRef<HTMLDivElement>(null);

  const handleCredential = useCallback(
    async (response: { credential: string }) => {
      const res = await fetch("/api/auth/callback", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ credential: response.credential }),
      });
      if (res.ok) router.push("/dashboard");
    },
    [router]
  );

  const handleScriptLoad = useCallback(() => {
    if (!window.google || !buttonRef.current) return;
    window.google.accounts.id.initialize({
      client_id: process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? "",
      callback: handleCredential,
    });
    window.google.accounts.id.renderButton(buttonRef.current, { theme: "outline", size: "large" });
  }, [handleCredential]);

  return (
    <div className={styles.page}>
      <Script src="https://accounts.google.com/gsi/client" strategy="afterInteractive" onLoad={handleScriptLoad} />
      <div className={styles.card}>
        <h1 className={styles.title}>SecondServe</h1>
        <p className={styles.subtitle}>Tableau de bord</p>
        <div ref={buttonRef} />
      </div>
    </div>
  );
}
