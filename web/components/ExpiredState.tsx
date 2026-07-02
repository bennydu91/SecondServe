export function ExpiredState() {
  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 12 }}>
      <h1 style={{ fontFamily: "var(--font-barlow), sans-serif", fontWeight: 800 }}>Ce lien n&apos;est plus disponible</h1>
      <p style={{ color: "#6A6F78" }}>Le suivi de ce match a expiré.</p>
    </div>
  );
}
