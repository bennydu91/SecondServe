export type NavItem = { href: string; label: string };

export const NAV_ITEMS: NavItem[] = [
  { href: "/dashboard", label: "Tableau de bord" },
  { href: "/dashboard/console", label: "Console de saisie" },
  { href: "/dashboard/history", label: "Historique" },
];

export function isNavItemActive(pathname: string, href: string): boolean {
  return href === "/dashboard" ? pathname === href : pathname.startsWith(href);
}
