# Déploiement SecondServe Backend sur VPS

## Prérequis

- VPS Ubuntu 22.04+ (4 vCPU / 16 Go RAM recommandés)
- `uv` installé (`curl -LsSf https://astral.sh/uv/install.sh | sh`)
- Tunnel Cloudflare configuré (remplace nginx + Certbot — le HTTPS est géré par Cloudflare)
- Domaine géré par Cloudflare

> **Note Python 3.12** : Python 3.12 n'est pas forcément disponible dans les dépôts apt du VPS. Ne pas l'installer manuellement — `uv` le télécharge automatiquement via la commande `uv sync` ci-dessous (voir étape 2).

## Étapes de déploiement

### 1. Copier les fichiers du backend sur le VPS

```bash
rsync -avz backend/ user@<vps-ip>:/opt/secondserve-backend/
```

### 2. Installer les dépendances

Le service systemd tourne sous l'utilisateur `www-data`, qui n'a pas accès à `/root/`. Il faut impérativement installer Python 3.12 et le `.venv` dans `/opt/` (lisible par tous) — **ne jamais copier le `.venv` depuis le poste de développement**.

```bash
# Créer le répertoire d'installation Python (une seule fois)
mkdir -p /opt/uv-python

# Installer Python 3.12 dans /opt/uv-python (accessible à www-data)
UV_PYTHON_INSTALL_DIR=/opt/uv-python uv python install 3.12

# Créer le venv et installer les dépendances
cd /opt/secondserve-backend
UV_PYTHON_INSTALL_DIR=/opt/uv-python UV_PYTHON=3.12 uv sync --no-dev
```

> **Pourquoi ces variables d'environnement ?** Sans `UV_PYTHON_INSTALL_DIR`, `uv` stocke Python dans `~/.local/share/uv/python/` (sous `/root/` si tu es root). Ce répertoire est en `0700` et inaccessible à `www-data`, ce qui provoque une erreur `Permission denied` au démarrage du service.

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
ssh user@<vps-ip> "cd /opt/secondserve-backend && UV_PYTHON_INSTALL_DIR=/opt/uv-python UV_PYTHON=3.12 uv sync --no-dev && uv run alembic upgrade head && sudo systemctl restart secondserve-backend"
```

> Ne jamais oublier `UV_PYTHON_INSTALL_DIR=/opt/uv-python` lors des mises à jour — si `uv` recréé le `.venv` sans cette variable, le service ne démarrera plus.
