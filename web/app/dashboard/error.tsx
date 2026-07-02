"use client";

export default function DashboardError({ reset }: { error: Error; reset: () => void }) {
  return (
    <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 16 }}>
      <h1 style={{ fontFamily: "var(--font-barlow), sans-serif", fontWeight: 800 }}>Impossible de charger le tableau de bord</h1>
      <p style={{ color: "#6A6F78" }}>Le serveur SecondServe est peut-être indisponible.</p>
      <button onClick={() => reset()}>Réessayer</button>
    </div>
  );
}
