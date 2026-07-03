# Déploiement SecondServe — Android

> **Le dépôt de développement et le build Gradle vivent sur le VPS** (`/root/SecondServe`, même machine que le backend). Les appareils physiques (Pixel 9 Pro en USB, Pixel Watch en débogage Wi-Fi) sont branchés en `adb` sur le **poste local** de Benny, pas sur le VPS. Il faut donc systématiquement rapatrier l'APK du VPS vers le poste local via `scp` avant de pouvoir l'installer — ne jamais lancer `./gradlew :app:installDebug` ou `:wear:installDebug` depuis le VPS, ça échoue avec `No connected devices!`.

## Prérequis

- JDK 17+ installé sur le VPS (`java -version`)
- ADB installé **sur le poste local** (`adb version`) — inclus dans le SDK Android
- Un accès SSH/`scp` du poste local vers le VPS pour rapatrier les APK
- Backend VPS opérationnel (voir `backend/DEPLOY.md`)
- Pixel 9 Pro et Pixel Watch sous tension, avec le mode développeur activé sur les deux
- Web Client ID Google Cloud configuré (voir section Google Sign-In dans `backend/DEPLOY.md`)

## Étape 1 — Configurer le Google Sign-In

L'authentification utilise Google Sign-In via le Credential Manager Android. Avant de builder, renseigner le **Web Client ID** obtenu dans Google Cloud Console (voir `backend/DEPLOY.md` section "Configuration Google Sign-In").

Éditer `app/src/main/res/values/google_auth.xml` :

```xml
<string name="google_web_client_id" translatable="false">VOTRE_WEB_CLIENT_ID.apps.googleusercontent.com</string>
```

> Ce fichier n'est **pas** commité avec la vraie valeur (placeholder uniquement). Le modifier localement avant chaque build de déploiement.

### SHA-1 pour l'Android Client ID Google Cloud

Google utilise le SHA-1 du keystore pour s'assurer que seule **ton** app (et pas une app tierce avec le même package name) peut obtenir des ID Tokens. Il faut donc déclarer le SHA-1 dans Google Cloud Console au moment où tu crées l'Android Client ID.

**Pourquoi deux SHA-1 ?** Android signe les APKs différemment selon le build :
- Le build **debug** utilise un keystore automatique créé par Android Studio/Gradle sur ta machine.
- Le build **release** utilise ton keystore personnel (`secondserve-release.jks`).
Google a besoin des deux pour que les deux variants fonctionnent.

**SHA-1 du keystore debug** (pour tester en staging) :

```bash
# Depuis le dossier android/
./gradlew signingReport
```

Dans la sortie, chercher le bloc correspondant au variant `debug` :
```
Variant: debug
...
SHA1: AA:BB:CC:DD:...   ← c'est cette valeur
```

**SHA-1 du keystore release** (une fois le keystore créé à l'étape 4b) :

```bash
keytool -list -v \
  -keystore secondserve-release.jks \
  -alias secondserve
```

Dans la sortie, chercher la ligne `SHA1:` dans la section "Certificate fingerprints".

**Où saisir ces valeurs :**
Dans **Google Cloud Console → APIs & Services → Credentials**, ouvrir l'Android Client ID créé dans `backend/DEPLOY.md` et ajouter les deux SHA-1 (un par ligne).

---

## Étape 2 — Mettre à jour l'URL du backend

Dans `app/build.gradle.kts`, remplacer `secondserve.example.com` par le domaine Cloudflare configuré pour le backend :

```bash
# Depuis le dossier android/
sed -i 's|https://secondserve.example.com/|https://api.ton-domaine.com/|g' app/build.gradle.kts
```

Vérifier le résultat :

```bash
grep "VPS_BASE_URL" app/build.gradle.kts
# Les lignes staging et release doivent pointer vers api.ton-domaine.com
# La ligne debug reste sur http://10.0.2.2:8000/ (émulateur uniquement)
```

---

## Étape 3 — Choisir le build à installer

| Variant | IA Gemini Nano | IA Mistral / VPS | Usage |
|---|---|---|---|
| `staging` | Mockée | Mockée | Test UI + backend, sans vraie IA |
| `release` | Réelle | Réelle | Test complet, nécessite un keystore |

**Pour un premier test (recommandé) : `staging`** — l'IA est simulée mais tout le reste (auth JWT, sync, historique, score Watch) est réel.

**Pour un test complet avec coaching IA : `release`** — voir section dédiée ci-dessous.

---

## Étape 4a — Build staging (premier test)

Sur le VPS :

```bash
cd /root/SecondServe/android/
./gradlew :app:assembleStaging
```

L'APK est généré dans :
```
app/build/outputs/apk/staging/app-staging.apk
```

---

## Étape 4b — Build release avec IA réelle

### Générer un keystore (une seule fois)

```bash
keytool -genkey -v \
  -keystore secondserve-release.jks \
  -alias secondserve \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```

> Conserver ce fichier en lieu sûr — ne jamais le committer dans git (déjà dans `.gitignore`).

### Ajouter la signing config dans `app/build.gradle.kts`

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../secondserve-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "secondserve"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ...
        }
    }
}
```

### Builder

Sur le VPS :

```bash
KEYSTORE_PASSWORD=<mot-de-passe> KEY_PASSWORD=<mot-de-passe> \
  ./gradlew :app:assembleRelease
```

L'APK est généré dans :
```
app/build/outputs/apk/release/app-release.apk
```

---

## Étape 5 — Installer sur le Pixel 9 Pro

### Activer le mode développeur

`Paramètres → À propos du téléphone → Numéro de build` (taper 7 fois)

Puis `Paramètres → Système → Options pour développeurs` :
- **Débogage USB** : activé
- (Optionnel) **Débogage sans fil** : activé pour ADB WiFi

### Rapatrier l'APK depuis le VPS

Depuis le **poste local** :

```bash
scp user@<vps-ip>:/root/SecondServe/android/app/build/outputs/apk/staging/app-staging.apk .
# ou
scp user@<vps-ip>:/root/SecondServe/android/app/build/outputs/apk/release/app-release.apk .
```

### Installer l'APK

Toujours depuis le **poste local** (les appareils ne sont pas branchés au VPS) :

```bash
# Vérifier que le téléphone est détecté
adb devices
# → Liste incluant le numéro de série du Pixel 9 Pro

# Installer (adapter le chemin selon le variant choisi)
adb install app-staging.apk
# ou
adb install app-release.apk
```

### Accorder la permission notifications

```bash
adb shell pm grant com.secondserve android.permission.POST_NOTIFICATIONS
```

---

## Étape 6 — Installer sur la Pixel Watch

La Pixel Watch se connecte en ADB via WiFi (pas de câble USB).

### Activer ADB sur la montre

Sur la Pixel Watch : `Paramètres → Système → À propos → Numéro de build` (taper 7 fois)

Puis `Paramètres → Options pour les développeurs → Débogage ADB` : activé

### Récupérer l'IP de la montre

`Paramètres → Connectivité → WiFi → (réseau connecté) → Adresse IP`

### Builder le module wear (sur le VPS)

```bash
cd /root/SecondServe/android/
./gradlew :wear:assembleDebug
```

### Rapatrier l'APK et installer (depuis le poste local)

```bash
# Rapatrier l'APK du VPS
scp user@<vps-ip>:/root/SecondServe/android/wear/build/outputs/apk/debug/wear-debug.apk .

# Connecter la montre en ADB WiFi (port par défaut 5555)
adb connect <ip-montre>:5555

# Vérifier la connexion
adb devices
# → Liste incluant <ip-montre>:5555

# Installer sur la montre (cibler par IP pour éviter l'ambiguïté)
adb -s <ip-montre>:5555 install wear-debug.apk
```

---

## Étape 7 — Vérifier Gemini Nano (build release uniquement)

Gemini Nano doit être présent sur le Pixel 9 Pro. Vérifier via ADB :

```bash
adb shell cmd aicore status
# Si "READY" → OK
# Si "DOWNLOADING" → attendre la fin du téléchargement
# Si absent → aller dans Paramètres → Applications → Gemini Nano → Activer
```

En cas d'absence, le build `release` bascule automatiquement sur l'`OfflineCoachingCache` puis les textes statiques — l'app reste fonctionnelle mais sans coaching IA live.

---

## Vérification du premier lancement

1. Ouvrir SecondServe sur le Pixel 9 Pro → l'écran "Se connecter avec Google" s'affiche
2. Appuyer sur **Se connecter avec Google** → sélectionner `ben.finot@gmail.com`
   - L'app envoie le Google ID Token au backend VPS
   - Le backend vérifie la signature RSA via les JWKS Google, contrôle l'email, émet un JWT SecondServe
   - Le JWT est stocké en `EncryptedSharedPreferences` — les lancements suivants passent directement à l'accueil
3. Renseigner le classement FFT et le profil
4. Ouvrir l'app Wear sur la Pixel Watch
5. Démarrer une session depuis la montre → vérifier que le score s'affiche sur le téléphone via DataLayer

```bash
# Logs en temps réel sur le téléphone
adb logcat -s SecondServe:D DataLayerListener:D CoachingResolver:D

# Logs en temps réel sur la montre
adb -s <ip-montre>:5555 logcat -s SecondServe:D TennisScoreEngine:D
```

---

## Mise à jour de l'app

Rebuild sur le VPS, puis rapatriement + réinstallation sans désinstaller (conserve les données Room) depuis le poste local. Penser à réinstaller **les deux APK** (téléphone + montre) si le fix touche `:wear` et `:app`/`:data`.

Sur le VPS :

```bash
cd /root/SecondServe/android/
./gradlew :app:assembleStaging
./gradlew :wear:assembleDebug
```

Depuis le poste local :

```bash
scp user@<vps-ip>:/root/SecondServe/android/app/build/outputs/apk/staging/app-staging.apk .
scp user@<vps-ip>:/root/SecondServe/android/wear/build/outputs/apk/debug/wear-debug.apk .

adb install -r app-staging.apk
adb -s <ip-montre>:5555 install -r wear-debug.apk
```
