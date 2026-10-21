# Localia pour Android

Application Android indépendante pour utiliser **LM Studio sur son PC**, avec connexion HTTPS authentifiée, chat en streaming, modèles du PC, extensions MCP dont DuckDuckGo, pièces jointes texte/image et historique chiffré sur le téléphone. Aucun modèle n’est exécuté sur Android dans cette version. Localia n’est pas affiliée à LM Studio et n’utilise pas l’appairage propriétaire LM Link.

## Connexion recommandée : Tailscale + HTTPS

1. Installer Tailscale sur le PC et Android ; connecter les deux appareils au même réseau privé Tailscale.
2. Dans LM Studio **0.4 ou supérieur**, démarrer le serveur sur `127.0.0.1:1234`, activer **Require Authentication** et créer un jeton avec les permissions nécessaires. L’exposition LAN peut rester désactivée.
3. Sur le PC :

   ```text
   tailscale serve --bg http://127.0.0.1:1234
   ```

4. Suivre la demande éventuelle d’activation HTTPS de Tailscale. Copier l’URL `https://nom-du-pc.…ts.net` dans Localia, puis le jeton LM Studio. **Vérifier et enregistrer** récupère les modèles de chat disponibles.
5. Choisir un modèle et envoyer un message. Le PC doit rester allumé ; LM Studio et Tailscale doivent rester actifs. Le premier message peut charger le modèle sur le PC.

Utiliser **Serve**, privé au réseau Tailscale, et non Funnel. Vérifier les règles d’accès Tailscale pour limiter les appareils autorisés. Aucun port du routeur n’a besoin d’être ouvert. Le HTTPS est terminé par Tailscale Serve sur le PC, puis transmis à LM Studio en boucle locale. Une autre passerelle HTTPS dotée d’un certificat reconnu est également possible.

Sources : [Tailscale Serve](https://tailscale.com/docs/reference/tailscale-cli/serve), [authentification LM Studio](https://lmstudio.ai/docs/developer/core/authentication).

## DuckDuckGo et autres outils

Dans LM Studio : panneau **Program → Install → Edit mcp.json**. Le nom juste sous `mcpServers` est à recopier dans Localia → Connexion → Outils et recherche Web. Par exemple, une entrée nommée `duckduckgo` devient `mcp/duckduckgo` dans la demande API.

Activer **Allow calling servers from mcp.json** dans le serveur LM Studio et autoriser l’utilisation des extensions pour le jeton. Choisir un modèle adapté aux appels d’outils. Les extensions s’exécutent sur le PC ; seules les intégrations explicitement configurées sont envoyées à LM Studio. Tous les outils des serveurs sélectionnés peuvent être utilisés, avec leurs droits sur le PC. Localia affiche les appels d’outils mais ne prétend pas pouvoir les approuver individuellement avant leur exécution par le serveur.

Source : [MCP via l’API LM Studio](https://lmstudio.ai/docs/developer/core/mcp).

## Confidentialité et limites

- HTTPS obligatoire, certificat et nom d’hôte vérifiés, aucun mode « ignorer le certificat », aucune redirection HTTP suivie. Jeton Bearer obligatoire côté client ; l’authentification doit aussi être activée dans LM Studio.
- Historique, réglages et jeton regroupés dans un coffre AES-256-GCM authentifié. Clé Android Keystore, écritures atomiques, stockage `noBackupFilesDir`, sauvegarde et transfert système désactivés. La désinstallation supprime les données locales ; la clé n’est pas exportée.
- Captures et aperçu des apps récentes bloqués par défaut. L’utilisateur peut désactiver cette protection. Les copies et exports texte sont des actions explicites et sortent du coffre chiffré.
- Pas de serveur Localia, publicité, télémétrie, suivi ni téléchargement de modèle sur Android.
- L’API native de LM Studio conserve le contexte côté PC (`store=true`, `previous_response_id`). Supprimer localement ne supprime pas les copies côté PC. Changer de serveur, jeton, modèle ou préférences nécessite une nouvelle conversation pour éviter de mélanger les contextes.
- Les requêtes DuckDuckGo et les autres outils peuvent contacter des services tiers depuis le PC.
- Texte UTF-8 : 200 Ko maximum ; images JPEG, PNG ou WebP : 4 Mo maximum, modèle vision requis. Pas de PDF ni traitement de documents par moteur de recherche documentaire dans cette version.
- La génération est interrompue lorsque l’application passe en arrière-plan, pour garder un comportement explicite. Les éventuelles actions déjà lancées sur le PC ne peuvent pas être annulées rétroactivement.
- Une compromission du téléphone déverrouillé ou du PC dépasse la protection du transport et du coffre. Aucun chiffrement ne garantit la fiabilité des réponses du modèle.

## Construire

JDK 17, SDK Android 36, Build Tools 35.0.0. Le wrapper Gradle utilise la version 8.11.1 avec empreinte de distribution vérifiée.

```text
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleRelease
```

Le debug utilise `fr.ben0813.localia.debug` ; la version destinée à Google Play utilisera `fr.ben0813.localia`. Le bundle release est **non signé** sans variables de signature. Il n’est pas prêt à être envoyé tel quel au store.

Pour produire un bundle signé avec une clé de téléversement détenue par l’éditeur, fournir `LOCALIA_KEYSTORE_PATH`, `LOCALIA_KEYSTORE_PASSWORD`, `LOCALIA_KEY_ALIAS` et `LOCALIA_KEY_PASSWORD` au processus de construction. Ne jamais ajouter la clé ni ses mots de passe au dépôt.

## Vérification

Les tests JVM couvrent HTTPS, certificats, nom d’hôte, refus des redirections, authentification, annulation, protocole MCP, conversations suivies, chiffrement et intégrité du coffre. Les tests Android couvrent Android Keystore, persistance après réouverture, corruption du fichier et parcours de connexion.

Le workflow fabrique l’APK et le bundle, vérifie signature/manifeste/ZIP/alignement 16 Ko, puis installe, teste et lance l’application dans un émulateur Android 16. Cela ne remplace pas les essais avec le vrai PC, DuckDuckGo et le Fairphone 6.

Voir [PLAY-READINESS.md](PLAY-READINESS.md) pour le travail restant avant publication.
