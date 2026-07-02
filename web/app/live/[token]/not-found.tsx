export default function NotFound() {
  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 12 }}>
      <h1 style={{ fontFamily: "var(--font-barlow), sans-serif", fontWeight: 800 }}>Lien invalide</h1>
      <p style={{ color: "#6A6F78" }}>Ce lien de suivi n&apos;existe pas.</p>
    </div>
  );
}
