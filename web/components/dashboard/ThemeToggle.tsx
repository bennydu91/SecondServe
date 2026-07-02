"use client";

import { useEffect, useState } from "react";
import styles from "./ThemeToggle.module.css";

type Theme = "light" | "dark";

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme | null>(null);

  useEffect(() => {
    const current = document.documentElement.getAttribute("data-theme");
    setTheme(current === "dark" ? "dark" : "light");
  }, []);

  function toggle() {
    const next: Theme = theme === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", next);
    localStorage.setItem("ss-theme", next);
    setTheme(next);
  }

  if (theme === null) return null;

  return (
    <button onClick={toggle} className={styles.toggle} aria-label="Changer de thème">
      {theme === "dark" ? "☀️" : "🌙"}
    </button>
  );
}
