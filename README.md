# FairLLM 0.4 - recherche Hugging Face et accélération Vulkan

Application Android pour discuter avec des modèles GGUF sur le téléphone, sans Termux.

## Utiliser

1. Installer l'APK sur un téléphone Android ARM64 (Android 9 minimum).
2. Dans **Modèles**, choisir Qwen3 1.7B (1,3 Go), Qwen3 4B Instruct 2507 (2,5 Go)
   ou toucher **Ajouter depuis Hugging Face**, chercher un nom et choisir un dépôt puis un fichier GGUF.
3. Toucher **Démarrer**, puis confirmer le téléchargement au premier lancement.
4. Attendre **Prêt**, puis envoyer un message dans **Chat**.
5. Utiliser **Arrêter le moteur** dans **Modèles** pour libérer la mémoire.

## Modèles et agents

La recherche appelle l'API Hugging Face, filtrée par GGUF et triée par téléchargements.
À l'ouverture, elle propose des modèles populaires. On peut rechercher par nom
sans connaître l'adresse du dépôt. Les modèles privés, soumis à autorisation,
et les modèles audio/vision identifiables par leurs métadonnées sont exclus.
La recherche est limitée à 50 résultats par requête : préciser le nom si nécessaire.
Le filtre des fichiers jusqu'à 3 Go est activé par défaut et peut être désactivé.
Il filtre la taille du téléchargement, sans garantir la mémoire nécessaire en fonctionnement.
Le choix du fichier ouvre une confirmation avant téléchargement et démarrage.

L'import accepte les dépôts publics Hugging Face sans restriction d'accès, avec
des fichiers GGUF en une seule partie. Les poids safetensors, modèles fragmentés
et projecteurs multimodaux ne sont pas importés. L'application récupère la taille
et l'empreinte SHA-256 et fixe la révision du dépôt avant téléchargement. Les
fichiers personnalisés sont stockés par empreinte pour éviter les collisions de noms.
Le format GGUF ne garantit pas que l'architecture du modèle est prise en charge
par la révision intégrée du moteur ou que la mémoire du téléphone suffit.
**Supprimer le téléchargement** libère le fichier local ; son entrée reste dans
la liste pour pouvoir le télécharger à nouveau.

Dans **Agents**, créer et modifier un profil : nom, rôle et consignes, modèle,
température, top-p, longueur maximum et consigne de prudence sur les faits.
Les profils sont conservés sur le téléphone. Choisir un agent depuis le menu du
chat ou **Converser**. Les conversations restent séparées pendant la session ;
elles ne sont pas sauvegardées après fermeture complète de l'application.
Changer d'agent avec un autre modèle arrête le moteur ; toucher Démarrer le recharge.
Un seul moteur et une seule génération sont actifs à la fois.

L'interface adapte sa largeur aux grands écrans. Le clavier réduit la zone des
messages, masque temporairement la navigation et garde la saisie et l'envoi
visibles. Les réglages et formulaires sont défilables.

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

Cette version intègre le backend **Vulkan** de llama.cpp. Dans Modèles, le mode
**GPU automatique** utilise un GPU compatible détecté ; sans GPU, il utilise le CPU.
Il ne réutilise pas le pilote OpenCL de Termux. Le nombre de couches GPU est
réglable de 1 à 99 (24 par défaut). Si le chargement échoue ou le pilote plante,
réduire les couches ou choisir **CPU** puis redémarrer. Un retour CPU n'est pas
automatiquement tenté après une erreur de pilote. Les variantes de matrices
coopératives sont désactivées pour privilégier la compatibilité mobile.

Le calcul utilise au maximum quatre threads, des lots de 256 tokens et des
sous-lots de 64 pour limiter les allocations temporaires. L'interface affiche
le texte au maximum environ 12 fois par seconde et réduit les défilements
pendant la génération. La vitesse fournie par le serveur est affichée en tokens/s
à la fin de la réponse. Le nombre de couches GPU effectivement chargé est lu
dans le journal et affiché quand il peut être confirmé.

Les gains et la compatibilité Vulkan doivent être mesurés sur le Fairphone réel,
à modèle, contexte et consignes identiques. Une compilation réussie ne démontre
ni un gain de vitesse ni une consommation mémoire inférieure sur le téléphone.
Qwen3 1.7B reste choisi par défaut pour limiter les besoins en mémoire.

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
python scripts/fetch-gpu.py
gradle :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

Le script récupère les sources nécessaires à la révision fixée.
Le compilateur hôte C/C++ et Ninja doivent être disponibles dans PATH pour
générer les shaders. Le compilateur GLSL est celui inclus dans le NDK. Vulkan-Headers
et SPIRV-Headers sont récupérés à des révisions fixes par fetch-gpu.py.
Sous Windows, FAIRLLM_HOST_TOOLCHAIN peut pointer vers un fichier CMake de
compilation hôte. Il ne change pas la compilation ARM64 de l'application.
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
- Saisie avec clavier, petit écran, rotation et texte agrandi : saisie accessible.
- Importer un GGUF public : taille visible, téléchargement, réponse et relance hors ligne.
- Créer deux agents avec des consignes différentes : sélectionner chacun,
  vérifier ses réglages et sa conversation, puis relancer l'app et retrouver les profils.

Les tests JVM couvrent le cache, la reprise HTTP, le contrôle d'intégrité,
les téléchargements incomplets, l'espace disponible et l'annulation.
Ils couvrent aussi les liens et métadonnées Hugging Face, l'isolation des fichiers,
la sérialisation des agents et la construction de leurs consignes.
Une compilation réussie ne remplace pas les tests de génération sur le téléphone.

