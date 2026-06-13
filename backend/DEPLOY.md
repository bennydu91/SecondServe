# Déploiement SecondServe Backend sur VPS

## Prérequis

- VPS Ubuntu 22.04+ (4 vCPU / 16 Go RAM recommandés)
- Python 3.12 installé
- `uv` installé (`curl -LsSf https://astral.sh/uv/install.sh | sh`)
- Nginx installé (`apt install nginx`)
- Certbot installé (`apt install certbot python3-certbot-nginx`)
- Domaine DNS pointant vers le VPS

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
# Éditer .env avec les vraies valeurs
nano /opt/secondserve-backend/.env
```

Variables à renseigner :
- `JWT_SECRET` : clé secrète forte (min. 32 caractères)
- `MISTRAL_API_KEY` : clé API Mistral (pour Epic 5)
- `DATABASE_URL` : `sqlite+aiosqlite:///./secondserve.db` (ou PostgreSQL en production)

### 4. Appliquer les migrations Alembic

```bash
cd /opt/secondserve-backend
uv run alembic upgrade head
```

### 5. Configurer le service systemd

```bash
cp secondserve-backend.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable secondserve-backend
systemctl start secondserve-backend
systemctl status secondserve-backend
```

### 6. Configurer Nginx

```bash
# Remplacer <vps-domain> par votre vrai domaine dans le fichier
sed 's/<vps-domain>/votre-domaine.com/g' nginx-secondserve.conf > /etc/nginx/sites-available/secondserve
ln -s /etc/nginx/sites-available/secondserve /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
```

### 7. Obtenir un certificat SSL avec Certbot

```bash
certbot --nginx -d votre-domaine.com
```

### 8. Vérifier le déploiement

```bash
curl https://votre-domaine.com/api/v1/health
# Réponse attendue : {"status": "ok"}
```

## Vérification que le service survive un redémarrage

```bash
reboot
# Après redémarrage :
systemctl status secondserve-backend
curl https://votre-domaine.com/api/v1/health
```

## Mise à jour du backend

```bash
rsync -avz backend/ user@<vps-ip>:/opt/secondserve-backend/
ssh user@<vps-ip> "cd /opt/secondserve-backend && uv sync --no-dev && uv run alembic upgrade head && systemctl restart secondserve-backend"
```
