# Déploiement SecondServe Web (page publique live) sur VPS

## Prérequis

- Node.js 20+ installé sur le VPS
- Le backend `secondserve-backend` déjà déployé et accessible en local sur le VPS (`http://127.0.0.1:<PORT>`)
- Tunnel Cloudflare déjà configuré pour le backend (`api.<ton-domaine>`)

## Étapes

### 1. Build en local puis transfert du build standalone

```bash
cd web
yarn build
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
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<Web Client ID Google — le même que GOOGLE_CLIENT_ID côté backend>
```

> `API_BASE_URL` (sans `NEXT_PUBLIC_`) est utilisé côté serveur (page publique, callback d'auth `/api/auth/callback`, tableau de bord `/dashboard`) — appel direct en local sur le VPS, pas via Cloudflare. `NEXT_PUBLIC_API_BASE_URL` est exposé au navigateur pour la connexion SSE de la page publique. `NEXT_PUBLIC_GOOGLE_CLIENT_ID` est exposé au navigateur pour afficher le bouton Google Identity Services sur `/login`.

### 2bis. Étape manuelle : autoriser le domaine desktop dans Google Cloud Console

Le Web Client ID Google existant (créé pour l'auth Android, cf. `docs/superpowers/plans/2026-06-25-google-signin-auth.md`) doit aussi autoriser le domaine du tableau de bord comme origine JavaScript :

1. https://console.cloud.google.com → APIs & Services → Credentials
2. Ouvrir le **Web Client ID** existant (celui utilisé pour `GOOGLE_CLIENT_ID` côté backend)
3. Dans **Authorized JavaScript origins**, ajouter `https://<ton-domaine>`
4. Enregistrer (peut prendre quelques minutes pour se propager)

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
cd web && yarn build
rsync -avz .next/standalone/ user@<vps-ip>:/opt/secondserve-web/
rsync -avz .next/static/ user@<vps-ip>:/opt/secondserve-web/.next/static/
ssh user@<vps-ip> "sudo systemctl restart secondserve-web"
```
