# Déploiement SecondServe Backend sur VPS

## Prérequis

- VPS Ubuntu 22.04+ (4 vCPU / 16 Go RAM recommandés)
- Python 3.12 installé
- `uv` installé (`curl -LsSf https://astral.sh/uv/install.sh | sh`)
- Tunnel Cloudflare configuré (remplace nginx + Certbot — le HTTPS est géré par Cloudflare)
- Domaine géré par Cloudflare

## Étapes de déploiement

### 1. Copier les fichiers du backend sur le VPS

```bash
rsync -avz backend/ user@<vps-ip>:/opt/secondserve-backend/
```

### 2. Installer les dépendances

```bash
cd /opt/secondserve-backend
uv sync --no-dev
```

### 3. Configurer les variables d'environnement

```bash
cp .env.example .env
nano /opt/secondserve-backend/.env
```

Variables à renseigner :

| Variable | Description |
|---|---|
| `JWT_SECRET` | Clé secrète forte, min. 32 caractères (`openssl rand -hex 32`) |
| `MISTRAL_API_KEY` | Clé API Mistral |
| `DATABASE_URL` | `sqlite+aiosqlite:///./secondserve.db` (défaut SQLite) |
| `PORT` | Port d'écoute uvicorn — choisir un port libre sur le VPS (ex. `8765`) |

### 4. Appliquer les migrations Alembic

```bash
cd /opt/secondserve-backend
uv run alembic upgrade head
```

### 5. Configurer le service systemd

```bash
sudo cp secondserve-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable secondserve-backend
sudo systemctl start secondserve-backend
sudo systemctl status secondserve-backend
```

Le service lit `PORT` depuis le fichier `.env` via `EnvironmentFile=`. Pas besoin de modifier le fichier `.service` si tu changes le port.

### 6. Configurer le tunnel Cloudflare

Dans le dashboard Cloudflare → **Zero Trust → Networks → Tunnels** :

1. Sélectionner ton tunnel ou en créer un nouveau
2. Ajouter un **Public Hostname** :
   - **Domain** : `api.ton-domaine.com` (ou le sous-domaine de ton choix)
   - **Service** : `http://localhost:<PORT>` (même valeur que `PORT` dans `.env`)
3. Sauvegarder — Cloudflare gère le HTTPS automatiquement

### 7. Vérifier le déploiement

```bash
# Vérification locale sur le VPS
curl http://localhost:<PORT>/api/v1/health
# → {"status": "ok"}

# Vérification depuis l'extérieur (via Cloudflare)
curl https://api.ton-domaine.com/api/v1/health
# → {"status": "ok"}
```

## Vérification que le service survive un redémarrage

```bash
sudo reboot
# Après redémarrage :
sudo systemctl status secondserve-backend
curl https://api.ton-domaine.com/api/v1/health
```

## Mise à jour du backend

```bash
rsync -avz backend/ user@<vps-ip>:/opt/secondserve-backend/
ssh user@<vps-ip> "cd /opt/secondserve-backend && uv sync --no-dev && uv run alembic upgrade head && sudo systemctl restart secondserve-backend"
```
