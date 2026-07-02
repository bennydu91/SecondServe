import type { Metadata } from "next";
import { barlowSemiCondensed, spaceGrotesk } from "@/lib/fonts";
import "./globals.css";

export const metadata: Metadata = {
  title: "SecondServe — Suivi live",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="fr" className={`${barlowSemiCondensed.variable} ${spaceGrotesk.variable}`}>
      <body>{children}</body>
    </html>
  );
}
