# FairLLM 0.2 - moteur autonome

Application Android pour discuter avec Qwen3 sur le téléphone, sans Termux.

## Utiliser

1. Installer l'APK sur un téléphone Android ARM64 (Android 9 minimum).
2. Choisir Qwen3 1.7B (1,3 Go) ou Qwen3 4B Instruct 2507 (2,5 Go).
3. Toucher **Démarrer**, puis confirmer le téléchargement au premier lancement.
4. Attendre **Moteur prêt**, puis envoyer un message.
5. Utiliser **Arrêter** dans Réglages pour libérer la mémoire.

Les poids proviennent de Hugging Face. Le téléchargement est repris après une
interruption et son SHA-256 est vérifié avant utilisation. Une fois le modèle
enregistré, les démarrages et les conversations fonctionnent hors ligne.
Les données restent dans le stockage privé de FairLLM. L'application ne réutilise
pas automatiquement les modèles stockés dans le dossier privé de Termux.

Le moteur est un service de premier plan dans un processus privé `:engine`.
Il peut continuer lorsque l'interface passe en arrière-plan. Android peut
néanmoins l'arrêter en cas de manque de mémoire ou de restriction de batterie.
Après un redémarrage du téléphone, toucher Démarrer suffit pour le relancer.

## Performances

Cette version utilise le **CPU**. Elle ne réutilise pas le pilote OpenCL de
Termux. Qwen3 1.7B est choisi par défaut pour limiter les besoins en mémoire.
L'accélération GPU doit être intégrée et testée séparément sur le Fairphone.
Les performances doivent être mesurées sur le téléphone réel.

## Architecture

- Jetpack Compose / Material 3 pour l'interface.
- llama.cpp intégré via JNI, à la révision exacte
  `46ca246de9bb1c35269722a6240d37d9dfd79cad`.
- Bibliothèque native ARM64 avec pages de 16 Ko.
- API locale sur `127.0.0.1:18080`, protégée par une clé privée de l'application.
- Téléchargement HTTPS et vérification des modèles dans un service Android.
- Aucun shell externe, permission Termux ou API d'inférence cloud.

## Compiler

Installer JDK 17, Gradle 8.9, Android SDK 35, NDK 28.2.13676358 et CMake 3.31.6.
Depuis la racine du projet :

```sh
python scripts/fetch-llama.py
gradle :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

Le script récupère les sources nécessaires à la révision fixée.
Le dossier `vendor` est ignoré par Git et n'a pas à être envoyé manuellement.
Le workflow **Build FairLLM APK** exécute les mêmes étapes pour chaque push,
pull request ou lancement manuel. L'APK se trouve dans l'artefact
**FairLLM-debug-apk**.

Les APK debug utilisent la clé du poste de compilation. Si une nouvelle version
est signée par une autre clé, Android demande de désinstaller l'ancienne version
de FairLLM avant installation ; cela efface les modèles privés de FairLLM.
Une distribution durable nécessite une clé de signature conservée séparément.

## Vérifier sur le téléphone

- Premier téléchargement : progression visible, puis Moteur prêt.
- Mode avion avec modèle déjà téléchargé : démarrage et réponse sans réseau.
- Annulation pendant le téléchargement : arrêt et reprise au lancement suivant.
- Arrêter, changer de modèle et Démarrer : seul le modèle sélectionné est chargé.
- Rotation / retour à l'application : progression et état du moteur conservés.
- Application quittée / Android arrête le service : état clair au retour.

Les tests JVM couvrent le cache, la reprise HTTP, le contrôle d'intégrité,
les téléchargements incomplets, l'espace disponible et l'annulation.
Une compilation réussie ne remplace pas les tests de génération sur le téléphone.

