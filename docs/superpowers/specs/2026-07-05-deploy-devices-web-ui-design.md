# Interface web locale pour deploy-devices.sh — Design

## Contexte

`scripts/deploy-devices.sh` (voir les specs précédentes du dossier) automatise déjà tout le cycle build (SSH sur le VPS) → rapatriement (scp) → installation (adb) sur le Pixel 9 Pro et la Pixel Watch, en ligne de commande. Ce chantier ajoute une interface web **strictement locale** (jamais déployée, jamais exposée sur le réseau) pour saisir la config et déclencher le script sans terminal, avec les logs affichés en direct dans le navigateur.

Cette interface doit rester complètement séparée de `web/` (l'app Next.js publique hébergée sur le VPS) : aucun risque qu'elle soit un jour accessible depuis l'extérieur.

## Objectif

- Un petit serveur Node sans framework, démarré manuellement (`node scripts/deploy-ui/server.js`), lié uniquement à `127.0.0.1` (pas d'authentification nécessaire — outil mono-utilisateur strictement local).
- Un formulaire web pour lire/éditer `scripts/deploy-devices.conf` (`VPS_HOST`, `VPS_USER`, `VPS_SSH_PORT`, `VPS_REPO_PATH`, `PHONE_IP`, `WATCH_IP`).
- Des cases à cocher pour cibler phone/watch et un interrupteur `--release` (avec saisie ponctuelle des mots de passe keystore, jamais persistés sur disque).
- Un lancement de `deploy-devices.sh` en sous-processus, avec streaming des logs en direct dans le navigateur.
- Une prise en charge du fallback interactif de saisie d'IP (dernier recours du script, après détection automatique et fallback config) via un champ de saisie qui apparaît dynamiquement dans l'UI quand le script l'exige.

## Architecture et fichiers

```
scripts/deploy-ui/
  server.js        — serveur HTTP : routes, spawn du script, streaming SSE, écriture stdin
  lib.js           — fonctions pures testables (voir ci-dessous)
  lib.test.js       — tests via `node --test` (natif, zéro dépendance)
  public/
    index.html     — formulaire config + panneau de lancement + panneau de logs
    app.js         — vanilla JS : fetch config, soumission, EventSource (SSE), rendu des logs
    style.css      — style minimal
```

**Fonctions pures dans `lib.js` (testées via `node --test`) :**
- `parseConfLines(lines)` → objet clé/valeur à partir du contenu du fichier de config (ignore commentaires/lignes vides).
- `updateConfLines(lines, updates)` → nouveau tableau de lignes : met à jour en place chaque clé déjà présente dans `updates`, ajoute les clés absentes en fin de fichier, laisse tout le reste (commentaires, clés non gérées par l'UI) inchangé. Même logique que `save_ip_to_config` côté bash, appliquée à plusieurs clés en une fois.
- `buildDeployArgs({ phone, watch, release })` → tableau de flags CLI (`[]`, `['--phone-only']`, `['--watch-only']`, `['--release']`, combinaisons). Lève une erreur si `phone` et `watch` sont tous les deux `false` (rien à déployer).
- `buildReleaseEnv({ release, keystorePassword, keyPassword })` → objet de variables d'environnement supplémentaires (vide si `release` est `false`).
- `parseAwaitingInputMarker(line)` → nom de variable (`"PHONE_IP"` / `"WATCH_IP"`) si la ligne correspond au marqueur `AWAITING_INPUT:<VAR>`, sinon `null`.

`server.js` (HTTP, spawn, SSE, écriture stdin) n'a pas de suite automatisée — vérifié manuellement en conditions réelles (adb/ssh non simulables dans l'environnement d'implémentation), comme pour `deploy-devices.sh` lui-même.

## Modification de `deploy-devices.sh`

Ajout d'un marqueur machine-lisible sur **stderr**, juste avant chaque `read -r` existant, indépendant du texte humain déjà affiché sur stdout (pour ne jamais coupler la détection de l'UI à une reformulation future des messages) :

```bash
echo "AWAITING_INPUT:WATCH_IP" >&2
read -r INPUT_IP
```
et symétriquement pour le phone avec `AWAITING_INPUT:PHONE_IP`. Aucun autre changement de comportement du script (les messages humains existants restent inchangés, toujours visibles en usage terminal direct).

## Flux fonctionnel

1. **Chargement** : `GET /api/config` lit `scripts/deploy-devices.conf`, pré-remplit le formulaire.
2. **Sauvegarde config** : `POST /api/config` applique `updateConfLines` et réécrit le fichier.
3. **Lancement** : `POST /api/deploy` avec `{ phone, watch, release, keystorePassword?, keyPassword? }`. Le serveur calcule les flags via `buildDeployArgs`, l'env supplémentaire via `buildReleaseEnv`, puis lance `scripts/deploy-devices.sh` en sous-processus avec un pipe sur stdin (pas fermé, contrairement à une première option écartée) pour pouvoir répondre aux prompts.
4. **Streaming** : stdout et stderr sont lus ligne par ligne et streamés au navigateur via Server-Sent Events. Sur stderr, chaque ligne passe par `parseAwaitingInputMarker` : si elle matche, elle n'est **pas** affichée dans le panneau de logs — un événement SSE `awaiting-input` est émis à la place avec le device concerné. Toutes les autres lignes (stdout et stderr) sont affichées telles quelles dans le panneau de logs, dans l'ordre où elles arrivent par flux (l'ordre relatif exact entre stdout et stderr n'est pas garanti à la ligne près, acceptable pour un outil de logs personnel).
5. **Saisie interactive** : à réception de `awaiting-input`, l'UI affiche un champ inline ("IP du phone :" / "IP de la watch :") + bouton "Envoyer". La soumission (`POST /api/deploy/input { value }`) écrit `value + "\n"` sur le stdin du sous-processus en cours (ligne vide acceptée = saut du device, comportement déjà existant du script), puis le champ disparaît et les logs reprennent. Un seul prompt actif à la fois (le script traite phone puis watch séquentiellement). Si aucun déploiement n'est en cours ou aucun prompt n'est ouvert, la route répond une erreur claire (409).
6. **Fin d'exécution** : à la sortie du sous-processus, un événement SSE final indique le code de sortie (succès si `0`, échec sinon) ; l'UI affiche un statut clair. Un seul déploiement à la fois — une tentative de lancement concurrent est refusée avec une erreur claire.

**Ordre de résolution des devices (rappel, comportement déjà existant et inchangé par ce chantier) :** détection automatique des devices déjà connectés (USB prioritaire pour le phone) → fallback via l'IP déjà en config (`PHONE_IP`/`WATCH_IP`) → fallback interactif (uniquement si les deux précédents échouent), c'est ce dernier niveau que cette UI prend en charge dynamiquement.

## Sécurité

- Le serveur écoute uniquement sur `127.0.0.1` — jamais `0.0.0.0`.
- Les mots de passe keystore ne sont jamais écrits sur disque ni loggués ; transmis uniquement en variables d'environnement au sous-processus, pour la durée de l'exécution.
- Pas d'authentification — le serveur n'est accessible que depuis la machine locale de l'utilisateur.

## Hors périmètre

- Pas de démarrage automatique/service en arrière-plan — lancement manuel à la demande.
- Pas de historique des déploiements passés persisté (les logs ne survivent pas au-delà de la session du navigateur).
- Pas de support multi-utilisateur ni de gestion de sessions.
