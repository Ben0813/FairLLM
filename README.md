# FairLLM

FairLLM est un client Android Compose pour utiliser `llama.cpp` localement sur un Fairphone 6 avec le backend OpenCL Adreno 810 déjà configuré dans Termux.

## Architecture

- **UI native Android** : Jetpack Compose / Material 3.
- **Moteur** : `llama-server` lancé dans Termux.
- **GPU** : le serveur hérite du `LD_LIBRARY_PATH` validé sur le Fairphone :
  `~/adreno-opencl:$PREFIX/lib:/vendor/lib64`.
- **API** : l'application dialogue uniquement avec `http://127.0.0.1:8080/v1/chat/completions`.
- **Streaming** : les tokens apparaissent au fil de la génération.
- **Vie privée** : aucune API cloud n'est nécessaire.

## Pré-requis sur le téléphone

La configuration actuelle doit déjà fonctionner dans Termux :

```bash
cd ~/llama.cpp
export LD_LIBRARY_PATH=$HOME/adreno-opencl:$PREFIX/lib:/vendor/lib64
./build/bin/llama-cli --list-devices
```

et afficher `GPUOpenCL: QUALCOMM Adreno(TM) 810`.

Ensuite, dans Termux :

```bash
mkdir -p ~/.termux
printf 'allow-external-apps=true\n' >> ~/.termux/termux.properties
termux-reload-settings
```

Redémarrer Termux. Dans Android, autoriser ensuite FairLLM à **Run commands in Termux environment** lorsque la permission est demandée.

## Modèles inclus comme presets

- Qwen3 4B Instruct 2507 — Q4_K_M — `-ngl 24` — qualité prioritaire.
- Qwen3 1.7B — Q4_K_M — `-ngl 99` — vitesse prioritaire.

Les modèles sont passés à `llama-server` avec `-hf`; le cache utilisé par `llama-cli` est réutilisé s'il existe déjà.

## Compiler

Ouvrir le dossier dans Android Studio, laisser Gradle synchroniser puis **Build > Build APK(s)**. Si Android Studio demande une distribution Gradle, choisir Gradle **8.10.2**.

Un workflow GitHub Actions (`.github/workflows/build-apk.yml`) est également inclus : après avoir poussé le projet sur GitHub, lancer **Build Android APK** puis télécharger l’artefact `FairLLM-debug-apk`.

Le projet cible Android 15 (API 35) et fonctionne sur Android 16.

## Ce que fait le bouton Démarrer

FairLLM demande à Termux de lancer en arrière-plan :

```bash
export LD_LIBRARY_PATH="$HOME/adreno-opencl:$PREFIX/lib:/vendor/lib64"
cd "$HOME/llama.cpp"
./build/bin/llama-server -hf <modele> -ngl <couches> -c 2048 --host 127.0.0.1 --port 8080
```

Les logs sont enregistrés dans :

```bash
~/fairllm/server.log
```

Pour diagnostiquer :

```bash
tail -f ~/fairllm/server.log
```

## Limites de cette v0.1

- Pas encore de téléchargement/gestion graphique de fichiers GGUF.
- Pas encore de pièces jointes ni de modèles vision.
- Le serveur tourne dans Termux plutôt que dans une bibliothèque native intégrée à l'APK. C'est volontaire : cela réutilise le backend OpenCL Adreno qui fonctionne déjà sur ce Fairphone sans se battre avec le linker namespace Android.
