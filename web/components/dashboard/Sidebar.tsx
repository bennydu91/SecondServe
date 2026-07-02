import styles from "./Sidebar.module.css";

export function Sidebar() {
  return (
    <aside className={styles.sidebar}>
      <div className={styles.logo}>SecondServe</div>
      <nav className={styles.nav}>
        <div className={styles.navItemActive}>
          <span className={styles.dot} />
          Tableau de bord
        </div>
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
