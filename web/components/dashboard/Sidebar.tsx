"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV_ITEMS, isNavItemActive } from "./navItems";
import styles from "./Sidebar.module.css";

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className={styles.sidebar}>
      <div className={styles.logo}>SecondServe</div>
      <nav className={styles.nav}>
        {NAV_ITEMS.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className={isNavItemActive(pathname, item.href) ? styles.navItemActive : styles.navItem}
          >
            <span className={styles.dot} />
            {item.label}
          </Link>
        ))}
      </nav>
      <div className={styles.profile}>
        <span className={styles.profileName}>Benjamin</span>
        <form action="/logout" method="POST">
          <button type="submit" className={styles.logoutButton}>
            Déconnexion
          </button>
        </form>
      </div>
    </aside>
  );
}
