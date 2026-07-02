# Déploiement SecondServe Web (page publique live) sur VPS

## Prérequis

- Node.js 20+ installé sur le VPS
- Le backend `secondserve-backend` déjà déployé et accessible en local sur le VPS (`http://127.0.0.1:<PORT>`)
- Tunnel Cloudflare déjà configuré pour le backend (`api.<ton-domaine>`)

## Étapes

### 1. Build en local puis transfert du build standalone

```bash
cd web
npm run build
rsync -avz .next/standalone/ user@<vps-ip>:/opt/secondserve-web/
rsync -avz .next/static/ user@<vps-ip>:/opt/secondserve-web/.next/static/
rsync -avz public/ user@<vps-ip>:/opt/secondserve-web/public/
```

### 2. Configurer les variables d'environnement sur le VPS

`/opt/secondserve-web/.env.production.local` :

```
PORT=3000
API_BASE_URL=http://127.0.0.1:8000
NEXT_PUBLIC_API_BASE_URL=https://api.<ton-domaine>
```

> `API_BASE_URL` (sans `NEXT_PUBLIC_`) est utilisé uniquement côté serveur (composant serveur de la page) — appel direct en local sur le VPS, pas via Cloudflare, pour éviter un aller-retour réseau inutile. `NEXT_PUBLIC_API_BASE_URL` est exposé au navigateur pour la connexion SSE et doit donc pointer vers l'URL publique du backend.

### 3. Mettre à jour le backend pour autoriser cette origine en CORS

Dans `/opt/secondserve-backend/.env` : `WEB_CORS_ORIGIN=https://<ton-domaine>`, puis `sudo systemctl restart secondserve-backend`.

### 4. Configurer le service systemd

```bash
sudo cp /opt/secondserve-web/secondserve-web.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable secondserve-web
sudo systemctl start secondserve-web
sudo systemctl status secondserve-web
```

### 5. Configurer le tunnel Cloudflare

Dashboard Cloudflare → **Zero Trust → Networks → Tunnels** → **Public Hostname** :
- **Domain** : `<ton-domaine>` (apex, cohérent avec `secondserve.app/live/{token}`)
- **Service** : `http://localhost:3000`

### 6. Vérifier le déploiement

```bash
curl -s http://localhost:3000/live/does-not-exist | grep -i "Lien invalide"
curl -s https://<ton-domaine>/live/does-not-exist | grep -i "Lien invalide"
```

## Mise à jour

```bash
cd web && npm run build
rsync -avz .next/standalone/ user@<vps-ip>:/opt/secondserve-web/
rsync -avz .next/static/ user@<vps-ip>:/opt/secondserve-web/.next/static/
ssh user@<vps-ip> "sudo systemctl restart secondserve-web"
```
