# Configuration Termux pour FairLLM

1. Vérifier l'Adreno :

```bash
cd ~/llama.cpp
export LD_LIBRARY_PATH=$HOME/adreno-opencl:$PREFIX/lib:/vendor/lib64
./build/bin/llama-cli --list-devices
```

Résultat attendu :

```text
GPUOpenCL: QUALCOMM Adreno(TM) 810
```

2. Autoriser les commandes externes :

```bash
mkdir -p ~/.termux
grep -q '^allow-external-apps=' ~/.termux/termux.properties 2>/dev/null \
  && sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties \
  || printf '\nallow-external-apps=true\n' >> ~/.termux/termux.properties
termux-reload-settings
```

3. Redémarrer Termux.

4. Dans FairLLM > Réglages > Démarrer, Android demandera la permission **Run commands in Termux environment**. L'accepter.

5. Si le chargement échoue :

```bash
cat ~/fairllm/server.log
```

6. Test manuel du serveur :

```bash
curl http://127.0.0.1:8080/health
```

Attendu lorsque le modèle est prêt :

```json
{"status":"ok"}
```
