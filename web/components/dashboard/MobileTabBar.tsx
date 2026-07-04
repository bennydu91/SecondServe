"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { NAV_ITEMS, isNavItemActive } from "./navItems";
import styles from "./MobileTabBar.module.css";

export function MobileTabBar() {
  const pathname = usePathname();

  return (
    <nav className={styles.tabBar}>
      {NAV_ITEMS.map((item) => (
        <Link
          key={item.href}
          href={item.href}
          className={isNavItemActive(pathname, item.href) ? styles.tabActive : styles.tab}
        >
          {item.label}
        </Link>
      ))}
    </nav>
  );
}
