# Confidentialité de Localia — brouillon à compléter avant publication

Date : 21 octobre 2026.

Éditeur et contact de confidentialité : **à renseigner par le propriétaire de l’application avant publication**.

Localia est un client Android indépendant permettant de communiquer avec un serveur LM Studio choisi par l’utilisateur. Aucun serveur, système publicitaire ni outil d’analyse Localia n’est intégré à cette version.

## Données sur le téléphone

Les paramètres de connexion, le jeton API et les conversations sont enregistrés dans un coffre chiffré AES-256-GCM. La clé est conservée dans Android Keystore. Les sauvegardes automatiques et transferts de ce coffre sont désactivés. Les fichiers joints sont lus uniquement à la demande de l’utilisateur via le sélecteur Android ; leur contenu n’est pas ajouté au coffre, mais leurs noms peuvent apparaître dans les messages. Les copies effectuées par Android ou un fournisseur de fichiers restent sous le contrôle de ce fournisseur.

## Données envoyées au PC et aux outils

Lorsque l’utilisateur envoie un message, son texte et sa pièce jointe éventuelle sont transmis par HTTPS au serveur qu’il a configuré. Le jeton est envoyé pour l’authentification. Le certificat du serveur est vérifié et les redirections ne sont pas suivies. L’application utilise le stockage de contexte de l’API LM Studio pour le suivi des conversations ; le PC peut donc conserver les messages, pièces jointes et résultats d’outils selon la configuration du serveur.

Si des extensions sont activées, celles-ci s’exécutent sur le PC et peuvent transmettre des informations à des services tiers, comme DuckDuckGo. L’utilisateur doit vérifier leurs permissions et leurs politiques de confidentialité. Tailscale est une solution de connexion facultative, administrée séparément par l’utilisateur et soumise à sa propre politique.

## Effacement, export et partage

L’utilisateur peut supprimer une conversation ou toutes les données de Localia depuis l’application. La désinstallation efface les données locales. Cela ne supprime pas les données déjà conservées par le serveur LM Studio ou les outils tiers : leur effacement doit être effectué sur ces systèmes.

Les copies, exports en texte et signalements partagés sont initiés explicitement par l’utilisateur. Ces informations sortent du coffre chiffré et sont remises à l’emplacement ou au destinataire choisi. L’utilisateur doit vérifier leur contenu avant le partage.

## Conservation et sécurité

Les conversations locales restent sur le téléphone jusqu’à leur suppression. Les captures d’écran et l’aperçu des applications récentes sont bloqués par défaut ; l’utilisateur peut désactiver cette protection. Le chiffrement ne protège pas contre un appareil ou un serveur déjà compromis et ne garantit pas l’exactitude des réponses des modèles.

## Contact et demandes

Le contact, la procédure de traitement des signalements et les modalités d’exercice des droits doivent être complétés par l’éditeur avant distribution publique.
