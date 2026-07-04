# Déploiement SecondServe Web (page publique live) sur VPS

> **Le dépôt de développement vit sur ce même VPS** (`/root/SecondServe`) — c'est aussi lui qui héberge les services en production. Le build s'exécute directement sur le VPS et le transfert vers `/opt/secondserve-web` est une simple copie locale, pas un envoi réseau vers une autre machine.

## Prérequis

- Node.js 20+ installé sur le VPS
- Le backend `secondserve-backend` déjà déployé et accessible en local sur le VPS (`http://127.0.0.1:<PORT>`)
- Tunnel Cloudflare déjà configuré pour le backend (`api.<ton-domaine>`)

## Étapes

### 1. Configurer les variables d'environnement AVANT le build

⚠️ **Next.js inline les variables `NEXT_PUBLIC_*` au moment du `build`, pas au démarrage du serveur.** Elles doivent donc exister dans le répertoire où tourne `yarn build` (`/root/SecondServe/web`, le dépôt de dev), pas seulement dans `/opt/secondserve-web`. Créer `/root/SecondServe/web/.env.production.local` (non commité, cf. `.gitignore`) :

```
API_BASE_URL=http://127.0.0.1:<PORT_BACKEND>
NEXT_PUBLIC_API_BASE_URL=https://api.<ton-domaine>
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<Web Client ID Google — le même que GOOGLE_CLIENT_ID côté backend>
```

> `API_BASE_URL` (sans `NEXT_PUBLIC_`) est utilisé côté serveur (page publique, callback d'auth `/api/auth/callback`, tableau de bord `/dashboard`) — appel direct en local sur le VPS, pas via Cloudflare. `NEXT_PUBLIC_API_BASE_URL` est exposé au navigateur pour la connexion SSE de la page publique. `NEXT_PUBLIC_GOOGLE_CLIENT_ID` est exposé au navigateur pour afficher le bouton Google Identity Services sur `/login`.
>
> **Piège vécu (2026-07-04)** : ce fichier manquait dans `/root/SecondServe/web` (seul `/opt/secondserve-web/.env.production.local` existait). Résultat : le bundle client contenait `client_id:""` au lieu de la vraie valeur → erreur Google "Accès bloqué : missing required parameter: client_id". Toujours vérifier après un build que la valeur est bien inlinée : `grep -rl "<fragment du client_id>" .next/` doit trouver un chunk.

### 2. Build sur le VPS puis copie locale du build standalone

```bash
cd /root/SecondServe/web
yarn build
rsync -avz .next/standalone/ /opt/secondserve-web/
rsync -avz .next/static/ /opt/secondserve-web/.next/static/
cp secondserve-web.service /opt/secondserve-web/
```

> Pas de `rsync public/` : le projet n'a pas (ou plus) d'assets statiques dans `web/public/` (scaffold `create-next-app` retiré). Si ce dossier réapparaît un jour, ajouter `rsync -avz public/ /opt/secondserve-web/public/`.

### 3. Configurer les variables d'environnement runtime sur le VPS

`/opt/secondserve-web/.env.production.local` (mêmes valeurs qu'à l'étape 1, plus `PORT`) :

```
PORT=3001
API_BASE_URL=http://127.0.0.1:<PORT_BACKEND>
NEXT_PUBLIC_API_BASE_URL=https://api.<ton-domaine>
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<Web Client ID Google — le même que GOOGLE_CLIENT_ID côté backend>
```

### 4. Étape manuelle : autoriser le domaine desktop dans Google Cloud Console

Le Web Client ID Google existant (créé pour l'auth Android, cf. `docs/superpowers/plans/2026-06-25-google-signin-auth.md`) doit aussi autoriser le domaine du tableau de bord comme origine JavaScript :

1. https://console.cloud.google.com → APIs & Services → Credentials
2. Ouvrir le **Web Client ID** existant (celui utilisé pour `GOOGLE_CLIENT_ID` côté backend)
3. Dans **Authorized JavaScript origins**, ajouter `https://<ton-domaine>`
4. Enregistrer (peut prendre quelques minutes pour se propager)

### 5. Mettre à jour le backend pour autoriser cette origine en CORS

Dans `/opt/secondserve-backend/.env` : `WEB_CORS_ORIGIN=https://<ton-domaine>` **(l'origine du domaine web, sans slash final — pas le domaine du backend)**, puis `sudo systemctl restart secondserve-backend`.

### 6. Configurer le service systemd

```bash
sudo cp /opt/secondserve-web/secondserve-web.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable secondserve-web
sudo systemctl start secondserve-web
sudo systemctl status secondserve-web
```

### 7. Configurer le tunnel Cloudflare

Dashboard Cloudflare → **Zero Trust → Networks → Tunnels** → **Public Hostname** :
- **Domain** : `<ton-domaine>` (apex, cohérent avec `secondserve.app/live/{token}`)
- **Service** : `http://localhost:3001`

### 8. Vérifier le déploiement

```bash
curl -s http://localhost:3001/live/does-not-exist | grep -i "Lien invalide"
curl -s https://<ton-domaine>/live/does-not-exist | grep -i "Lien invalide"
```

## Mise à jour

Toujours en local sur le VPS, depuis le dépôt de dev. Nécessite que `/root/SecondServe/web/.env.production.local` existe déjà (voir étape 1) — sans lui, les variables `NEXT_PUBLIC_*` sont vides dans le nouveau build.

```bash
cd /root/SecondServe/web && yarn build
rsync -avz .next/standalone/ /opt/secondserve-web/
rsync -avz .next/static/ /opt/secondserve-web/.next/static/
sudo systemctl restart secondserve-web
```
