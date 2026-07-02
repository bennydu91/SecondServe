type Props = { status: "WAITING" | "LIVE" | "ENDED" };

export function LiveBadge({ status }: Props) {
  if (status === "ENDED") {
    return (
      <span
        style={{
          display: "inline-flex",
          alignItems: "center",
          gap: 7,
          background: "#14161A",
          color: "#F4F4F1",
          fontFamily: "var(--font-barlow), sans-serif",
          fontWeight: 800,
          fontSize: 12,
          letterSpacing: 1.5,
          padding: "6px 12px",
          borderRadius: 100,
        }}
      >
        TERMINÉ
      </span>
    );
  }
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 7,
        background: "#E63958",
        color: "#fff",
        fontFamily: "var(--font-barlow), sans-serif",
        fontWeight: 800,
        fontSize: 12,
        letterSpacing: 1.5,
        padding: "6px 12px",
        borderRadius: 100,
      }}
    >
      <span
        style={{
          width: 7,
          height: 7,
          borderRadius: "50%",
          background: "#fff",
          animation: status === "LIVE" ? "ssPulse 1.4s infinite" : "none",
        }}
      />
      {status === "WAITING" ? "EN ATTENTE" : "EN DIRECT"}
    </span>
  );
}
