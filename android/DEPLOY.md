# Déploiement SecondServe — Android

## Prérequis

- JDK 17+ installé (`java -version`)
- ADB installé (`adb version`) — inclus dans le SDK Android (`/root/android-sdk/platform-tools/`)
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

Pour que Google autorise l'app à émettre des ID Tokens, l'Android Client ID Google Cloud doit être lié au SHA-1 du keystore utilisé pour signer l'APK.

```bash
# SHA-1 du keystore debug (pour les tests)
cd android/
./gradlew signingReport 2>&1 | grep -A3 "Variant: debug"

# SHA-1 du keystore release
keytool -list -v -keystore secondserve-release.jks -alias secondserve | grep "SHA1:"
```

Ajouter chaque SHA-1 dans **Google Cloud Console → Credentials → Android Client ID**.

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

```bash
cd android/
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

### Installer l'APK

```bash
# Vérifier que le téléphone est détecté
adb devices
# → Liste incluant le numéro de série du Pixel 9 Pro

# Installer (adapter le chemin selon le variant choisi)
adb install app/build/outputs/apk/staging/app-staging.apk
# ou
adb install app/build/outputs/apk/release/app-release.apk
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

### Connecter et installer

```bash
# Connecter la montre en ADB WiFi (port par défaut 5555)
adb connect <ip-montre>:5555

# Vérifier la connexion
adb devices
# → Liste incluant <ip-montre>:5555

# Builder le module wear
./gradlew :wear:assembleDebug

# Installer sur la montre (cibler par IP pour éviter l'ambiguïté)
adb -s <ip-montre>:5555 install wear/build/outputs/apk/debug/wear-debug.apk
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

```bash
# Rebuild et réinstaller sans désinstaller (conserve les données Room)
./gradlew :app:assembleStaging && adb install -r app/build/outputs/apk/staging/app-staging.apk
./gradlew :wear:assembleDebug && adb -s <ip-montre>:5555 install -r wear/build/outputs/apk/debug/wear-debug.apk
```
