# Préparation Google Play — version de test

## Préparé dans le projet

- Identité d’application indépendante ; aucun logo, nom commercial ou visuel de Locally repris.
- Android 16 / API 36 ciblé. [Exigence Google actuelle](https://developer.android.com/google/play/requirements/target-sdk).
- APK de test distinct du paquet de production et génération du format Android App Bundle.
- Signature release fournie par variables d’environnement ; aucune clé de production dans les sources.
- HTTPS imposé, permissions limitées à Internet, accès aux fichiers via le sélecteur Android, coffre local chiffré, effacement local disponible.
- Notice intégrée sur les données envoyées au PC, les outils tiers, le stockage serveur et les exports.
- Action de signalement sur chaque réponse : partage explicite du texte choisi. **Un destinataire de support et un circuit de traitement des signalements doivent être définis avant publication.**

## À finaliser avec l’éditeur avant une soumission

1. Valider le nom « Localia », l’identifiant définitif, le titulaire de l’application et le compte développeur Google Play.
2. Créer et sauvegarder une clé de téléversement ; activer Play App Signing. Ne pas employer une clé debug pour la production.
3. Fournir une adresse de support réelle et héberger la politique de confidentialité à une URL publique. Le brouillon `PRIVACY-POLICY.md` doit être complété et relu.
4. Renseigner Data safety, contenu, classification d’âge et déclarations relatives à l’IA à partir du comportement final. L’absence de serveur Localia ne dispense pas de décrire le transfert volontaire vers le serveur configuré et l’éventuel recours à des outils tiers.
5. Finaliser le signalement des réponses vers le support, son traitement et les mesures applicables aux contenus générés. Vérifier les [règles relatives au contenu généré par IA](https://support.google.com/googleplay/android-developer/answer/13985936).
6. Tester le PC LM Studio réel, Tailscale en Wi-Fi et réseau mobile, les expirations/révocations de jetons, un téléphone Android 9 et Android 16, les grandes tailles de police, le mode sombre et la reprise après arrêt du processus.
7. Préparer des captures réelles, description, icône de fiche 512 × 512 et visuel de présentation ; valider l’accessibilité et les traductions souhaitées.
8. Passer par les pistes de test et les éventuelles exigences de test applicables au compte développeur. Consulter les exigences actuelles de Play Console au moment de soumettre.

Le bundle produit ici est un artefact de développement. Aucune publication ni création de compte payant n’est effectuée.
