# Investigation: Audit dysfonctionnements Watch ↔ Phone (démarrage & sync score)

## Hand-off Brief

1. **What happened.** Trois symptômes liés : la montre ne propose pas les options complètes de match, le démarrage depuis le téléphone n'ouvre pas l'app montre, et les points marqués sur la montre ne remontent pas au téléphone (donc pas de coaching au changement de côté).
2. **Where the case stands.** Cause racine commune **confirmée au niveau code** : l'ouverture d'app inter-appareils repose sur un `startActivity()` appelé depuis un `WearableListenerService` (arrière-plan), bloqué par les restrictions Android *Background Activity Launch* (BAL, API 29+). Cela casse les deux sens et cascade sur les 3 symptômes. Symptôme 1 = en partie limite de conception (la montre n'a jamais été spécifiée pour configurer surface/adversaire).
3. **What's needed next.** Remplacer les `startActivity()` cross-device par des **notifications full-screen-intent** (ou data-item + activité déjà au premier plan), puis re-tester le flux complet. Recommandé : `bmad-quick-dev` sur les deux listeners.

## Case Info

| Field            | Value |
| ---------------- | ----- |
| Ticket           | N/A |
| Date opened      | 2026-06-27 |
| Status           | Active |
| System           | Pixel 9 Pro (Android 15+) + Pixel Watch (Wear OS 5 = Android 14). Apps `:app` et `:wear`, `applicationId = com.secondserve` (commun, routage Wearable OK). |
| Evidence sources | Code source Android/Wear, specs `_bmad-output` (architecture.md, stories 2.2/2.3/2.4) |

## Problem Statement

Benny rapporte (verbatim) : démarrage sur la montre → choix « 3 sets » mais pas d'accès aux autres options du match ; démarrage sur le téléphone → ne réveille pas l'app montre ; points marqués sur la montre absents du téléphone → pas de déclenchement coaching au changement de côté. « Bref, rien ne fonctionne ».

## Confirmed Findings

### Finding 1 — Ouverture montre depuis le téléphone = startActivity en arrière-plan
**Evidence:** `android/wear/src/main/kotlin/com/secondserve/wear/WearDataLayerListener.kt:40` (`applicationContext.startActivity(intent)` dans `onMessageReceived` d'un `WearableListenerService`).
**Detail:** Le téléphone envoie `PATH_START_SESSION` (`NewMatchViewModel.kt:107`). La montre reçoit dans un service système et tente de lancer `WearActivity` directement. Aucun fallback notification.

### Finding 2 — Ouverture téléphone depuis la montre = même pattern bloquant
**Evidence:** `android/data/src/main/kotlin/com/secondserve/data/wearable/DataLayerListener.kt:206` (`applicationContext.startActivity(intent)` vers `OpenMatchAlias`, depuis `handleStartSessionRequest`).
**Detail:** Quand la montre démarre (`StartMatchViewModel.confirmStart` → `PATH_START_SESSION_REQUEST`), le téléphone crée la session puis tente d'ouvrir `MainActivity` via startActivity depuis le service.

### Finding 3 — Le pipeline score lui-même est correctement câblé
**Evidence:** `ScoreModule.kt:15-17` (`@Binds @Singleton bindScoreRepository`), `ScoreRepositoryImpl.kt:14-18` (StateFlow in-memory), `DataLayerListener.kt:119-154` (handleScoreEvent/handleGameOver → `scoreRepository.updateScore` + `dataLayerEventBus.emitGameOver`), `DataModule.kt:116-117` (`@Singleton DataLayerEventBus`), `MatchViewModel.kt:48-56` (collecte `gameOverEvents` → `coachingResolver.resolve`).
**Detail:** Repository ET event bus sont des singletons partagés service↔ViewModel. Donc si l'écran Match du téléphone est ouvert ET la montre sur le ScoreScreen, le score remonte et le coaching se déclenche. Le pipeline n'a pas de bug d'instance.

### Finding 4 — La montre n'a jamais été spécifiée pour configurer surface/adversaire
**Evidence:** architecture.md:76 (« Contrat unidirectionnel principal : Watch → Phone: ScoreEvent »), story 2.3 AC1 (formulaire complet — surface/format/adversaire/compétition/tournoi — côté **téléphone**), `StartMatchScreen.kt:60-96` (montre = format + règle 3e set uniquement), `DataLayerListener.kt:175` (`Session(surface = "", ...)` quand créée depuis la montre).
**Detail:** L'écran de démarrage montre propose bien 1/3 sets ET la règle du 3e set (visibles par défaut puisque `matchFormat` défaut = `BEST_OF_3`). Les « autres options » (surface, adversaire…) sont par conception réservées au téléphone.

### Finding 5 — Paths d'ouverture cross-device = ajouts hors architecture initiale
**Evidence:** story 2.2 (table des paths : seuls `score_event`, `game_over`, `coaching_result[supprimé]`), architecture.md:814-816 (flux conçu : `Wear tap → DataLayerListener → game_over → CoachingResolver`).
**Detail:** `PATH_START_SESSION`, `PATH_START_SESSION_REQUEST`, `PATH_CLOSE_SESSION` ont été ajoutés au-delà du contrat unidirectionnel d'origine, sans mécanisme de réveil d'app fiable.

### Finding 6 — `emitStartSession` est du code mort
**Evidence:** `DataLayerEventBus.kt:22-27` (`startSessionRequests`/`emitStartSession`) émis en `DataLayerListener.kt:190` mais **aucun collecteur** (grep : pas de consommateur de `startSessionRequests`). La navigation téléphone repose uniquement sur l'Intent `ACTION_OPEN_MATCH` (`MainActivity.kt:139-143` → `AppNavGraph.kt:102-104`).
**Detail:** Mineur, mais révèle que l'ouverture côté téléphone dépend exclusivement du startActivity bloqué (Finding 2).

## Deduced Conclusions

### Deduction 1 — Symptôme 2 (téléphone n'ouvre pas la montre) = BAL
**Based on:** Finding 1.
**Reasoning:** Android 10+ interdit à une app en arrière-plan de lancer une Activity sauf exceptions (fenêtre visible, full-screen-intent, etc.). Un `WearableListenerService` réveillé par un message n'a aucune de ces conditions. Wear OS 5 applique cette politique.
**Conclusion:** Le message arrive, le service tourne, mais `startActivity` est refusé silencieusement → l'app montre ne s'ouvre pas.

### Deduction 2 — Symptôme 3 (pas de score/coaching sur téléphone) = cascade BAL
**Based on:** Findings 2, 3, Deduction 1.
**Reasoning:** Le pipeline score est correct (Finding 3), donc le blocage est en amont : aucun écran Match vivant côté téléphone.
- Démarrage **montre** → ouverture téléphone bloquée (Finding 2) → pas de `MatchViewModel` → `gameOver` part dans le vide.
- Démarrage **téléphone** → écran Match ouvert (navigation in-app OK), mais ouverture montre bloquée (Deduction 1) → la montre reste sur l'accueil, pas sur le ScoreScreen → n'envoie aucun point.
**Conclusion:** Dans les deux sens, un côté n'est jamais sur le bon écran → aucun point ne transite de bout en bout.

### Deduction 3 — Symptôme 1 = limite de conception + aggravée par le symptôme 2
**Based on:** Findings 4, 5, Deduction 1.
**Reasoning:** La montre ne configure volontairement que le format. Le flux nominal attendu (configurer sur le téléphone, la montre s'ouvre pour scorer) est cassé par BAL → l'utilisateur se rabat sur le démarrage montre, où les options sont limitées et la session créée avec `surface=""`.
**Conclusion:** Perçu comme un bug ; c'est un mélange de conception (montre = contrôleur léger) et d'effet de bord du symptôme 2.

## Hypothesized Paths

- **H1 (Open):** Une partie du non-fonctionnement pourrait aussi venir de la non-livraison des messages (nœud non connecté / app jamais lancée). Confirmable via Logcat : présence des logs `DataLayerListener: received path=...` / `WearDataLayerListener: received path=...`. Si présents → c'est bien le startActivity qui échoue (BAL confirmé). Si absents → problème de couche transport en amont.

## Final Conclusion

**Confidence: Medium-High.** Cause racine **commune et confirmée au niveau code** : ouverture d'app inter-appareils via `startActivity()` depuis un `WearableListenerService` (Findings 1 & 2), bloquée par les restrictions Background Activity Launch (Deductions 1 & 2). Le pipeline de score/coaching est sain (Finding 3) ; il ne s'exécute jamais faute d'avoir les deux écrans ouverts simultanément. Symptôme 1 = limite de conception aggravée (Deduction 3). La seule incertitude restante (H1, transport) se lève en 5 min via Logcat.

## Fix Direction

1. **Réveil d'app fiable (cœur du correctif).** Remplacer les `startActivity()` des deux listeners par une **notification avec `setFullScreenIntent(...)`** (+ canal haute priorité, permission `USE_FULL_SCREEN_INTENT`). C'est le pattern supporté pour amener une Activity au premier plan depuis l'arrière-plan. Concerne `WearDataLayerListener.kt:35-40` et `DataLayerListener.kt:201-206`.
2. **Cohérence des données montre→téléphone.** Décider si la montre doit pouvoir saisir surface/adversaire (sinon afficher « surface non renseignée » et permettre l'édition a posteriori côté téléphone) — éviter `surface=""`.
3. **Nettoyage.** Supprimer `emitStartSession`/`startSessionRequests` (code mort, Finding 6) ou le brancher à la navigation.

## Follow-up: 2026-06-27 — Logs runtime : hypothèse BAL RÉFUTÉE, cause racine CONFIRMÉE

Logcat capturé sur les deux appareils (`logs/log_phone-20260627-155245.txt`, `logs/log_watch-20260627-155245.txt`).

### Finding 7 (CONFIRMÉ) — GMS ne peut pas se lier aux WearableListenerService : « bind: Permission denied »
**Evidence (Watch):** `log_watch:2` — `W/WearableService bind: Permission denied connecting to ServiceRecord[...wear.WearDataLayerListener..., action=/secondserve/start_session...]` (juste après l'envoi téléphone `log_phone:136` `sent /secondserve/start_session`).
**Evidence (Phone):** `log_phone:189` (`start_session_request`), `log_phone:204..354` (TOUS les `score_event` et `game_over`) — `W/WearableService bind: Permission denied connecting to ServiceRecord[...data.wearable.DataLayerListener...]`.
**Detail:** Les messages SONT livrés à Google Play Services sur l'appareil cible, mais GMS **n'arrive pas à binder** le `WearableListenerService` → `onMessageReceived` n'est **jamais** appelé. Aucun handler ne tourne, dans aucun sens.

### Refutation — Deductions 1 & 2 (BAL) sont RÉFUTÉES
**Evidence:** `log_watch:5` et `log_phone:11` montrent des lancements d'Activity réussis avec `(BAL_ALLOW_VISIBLE_WINDOW) result code=0`. Le BAL n'est donc pas le bloqueur ; de toute façon le service listener ne s'exécute jamais (Finding 7), donc le `startActivity` n'est même pas atteint.
**Evidence corroborante:** `log_watch:69` — timeout côté montre exactement 30 s après l'envoi (`StartMatchViewModel: timeout — téléphone n'a pas répondu`), cohérent avec un téléphone qui ne traite jamais la requête.

### Deduction 4 (CONFIRMÉE) — Cause racine = attribut `android:permission` sur les `<service>`
**Based on:** Finding 7 + manifests.
**Reasoning:** Les deux déclarations portent `android:permission="com.google.android.gms.wearable.BIND_LISTENER"` (`android/app/src/main/AndroidManifest.xml` service `DataLayerListener` ; `android/wear/src/main/AndroidManifest.xml` service `WearDataLayerListener`). Cette permission héritée n'est plus accordée au process GMS moderne → exiger cette permission sur le service fait échouer le `bind()` de GMS avec « Permission denied ». C'est la cause directe et unique du Finding 7.
**Conclusion:** Supprimer l'attribut `android:permission="com.google.android.gms.wearable.BIND_LISTENER"` des deux services (garder `android:exported="true"` + l'`intent-filter`). C'est un correctif de 2 lignes (une par manifest).

### Impact corrigé sur les 3 symptômes
- **Symptôme 2 & 3 :** causés par Finding 7 (bind refusé). Le pipeline score est sain (Finding 3) ; il ne s'exécutait jamais car le service ne démarrait pas. Le fix manifest débloque les deux.
- **Risque résiduel à vérifier après fix :** une fois le service capable de tourner, `WearDataLayerListener` appelle `startActivity` depuis l'arrière-plan (Finding 1) — à re-tester pour confirmer que l'ouverture montre fonctionne (sinon fallback full-screen-intent). Les `score_event`/`game_over` (symptôme 3) n'impliquent aucun `startActivity` → réglés par le seul fix manifest.
- **Symptôme 1 :** inchangé (limite de conception, Deduction 3).

### Final Conclusion (révisée) — Confidence: HIGH
Cause racine **confirmée par observation directe runtime** : `android:permission="com.google.android.gms.wearable.BIND_LISTENER"` sur les deux `WearableListenerService` empêche Google Play Services de les binder (« bind: Permission denied »), donc aucun message DataLayer n'est traité dans aucun sens. Correctif déterministe : retirer cet attribut des deux manifests.

## Follow-up: 2026-06-27 #2 — Après fix manifest : BAL confirmé + coaching à un seul affichage

Logs `logs/*-181728.txt`. Le pont fonctionne (plus de « Permission denied »), montre→téléphone OK.

### Finding 8 (CONFIRMÉ) — startActivity depuis le service montre bloqué par BAL
**Evidence:** `log_watch:10` — `E/ActivityTaskManager: Background activity launch blocked! ... callingUidProcState: CACHED_EMPTY ... cmp=.../.wear.WearActivity ... result code=102 (BAL_BLOCK)`. Le lancement manuel ultérieur passe (`log_watch:19`, `BAL_ALLOW_VISIBLE_WINDOW`).
**Detail:** Confirme la Deduction 1 (réhabilitée pour ce cas précis) : une fois le service capable de tourner, le `startActivity` à froid est refusé. Côté téléphone le launch a réussi car l'app était au premier plan (fenêtre visible).
**Fix appliqué:** remplacement des `startActivity` par une notification full-screen-intent dans `WearDataLayerListener` ET `DataLayerListener` (+ permission `USE_FULL_SCREEN_INTENT` sur les deux manifests).

### Finding 9 (DÉDUIT) — Un seul conseil visible : pas de ré-annonce dans l'UI
**Evidence:** `log_phone:154` (gameOver 1-0) et `log_phone:181` (gameOver 1-2) tous deux émis. `CoachingResolver.resolve` ne retourne jamais null (fallback statique). `MatchScreen.kt:86` affichait la carte via `AnimatedVisibility(visible = coachingAdvice != null)` jamais remis à null.
**Reasoning:** patterns différents (1-0 → FIRST_GAME_WON, 1-2 → NEUTRAL_TRANSITION) mais la carte restant visible en continu, un nouveau conseil change le texte en place sans se ré-annoncer (et aucune recomposition si texte identique).
**Fix appliqué:** `coachingAdviceSeq` incrémenté à chaque changement de côté + `key(seq)` autour du `AnimatedVisibility` (animation rejouée à chaque conseil). À confirmer via capture (filtre `capture-logs.sh` élargi à `Coaching`).

## Diagnostic / Verification Plan

1. Logcat filtré `secondserve` pendant un démarrage téléphone : vérifier `WearDataLayerListener: received path=/secondserve/start_session` puis observer si `WearActivity` s'ouvre (lève H1 et confirme BAL).
2. Idem démarrage montre : `DataLayerListener: received path=/secondserve/start_session_request` puis ouverture (ou non) de `MainActivity`.
3. Après correctif full-screen-intent : rejouer les deux sens + marquer un jeu complet et vérifier le coaching au changement de côté.
