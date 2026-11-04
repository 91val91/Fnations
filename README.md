# FNations — Mod Fabric + Launcher

Serveur Minecraft **1.21.4** de nations : recherche & développement, guerres, banque, chambres (WorldEdit), armes à feu,
véhicules (char, avion, hélicoptère, tracteur), machines et énergie, missiles, fusée spatiale (Orbite, Lune).

Le projet est maintenant un **vrai mod Fabric** (client + serveur). Il est distribué par un **launcher maison**
qui se connecte avec un **vrai compte Microsoft**, installe Minecraft, Fabric, Java et les mods, puis lance le jeu
directement sur le serveur.

```
Fnations/
├── mod/        Mod Fabric (Java 21, Loom, Mojang mappings) : tous les systèmes, GUIs, modèles, données
├── launcher/   Launcher Electron : connexion Microsoft, installation, synchronisation du modpack, lancement
├── server/     Modèle de serveur Fabric (server.properties, scripts de démarrage)
└── tools/      build.sh (tout construire) et generate_resourcepack.py (modèles/textures/langues du mod)
```

> L'ancien plugin Paper a été entièrement porté dans le mod et retiré du dépôt. Il reste disponible dans l'historique git.

---

## 1. Le mod FNations

### Ce qui change par rapport au plugin

| Système | Avant (plugin + resource pack) | Maintenant (mod) |
|---|---|---|
| Items | 59 items (base PAPER + modèle du pack) | 59 vrais items enregistrés, données dans des composants (chargeur, billets, carburant) |
| Machines | Blocs vanilla avec un display par-dessus | 10 vrais blocs orientables avec entité de bloc, compatibles entonnoirs, cassés à la pioche. Panneaux solaires et Station de Craft en 3D animée |
| GUIs | Coffres Bukkit | Panneaux au thème FNations, écrans de machine avec jauges, vraie fenêtre de saisie |
| Armes | Clic droit, F pour recharger | Tir maintenu pour les armes automatiques, recul de caméra, **R** pour recharger, HUD des munitions, zoom de lunette |
| Véhicules | Armor stands téléportés | Vraies entités avec collisions, conduite fluide simulée par le client (comme les bateaux), rotor animé |
| Missiles, fusée | Item displays | Entités rendues en 3D. La fusée voyage vers les **dimensions** `fnations:orbit` et `fnations:moon` |
| Chambres | WorldEdit obligatoire | WorldEdit Fabric si installé (`.schem`), sinon structures vanilla (`.nbt`) |
| Datapack | À part | Intégré au mod : recette de la Station, progrès, loot tables, dimensions |

### Systèmes

Commandes : `/menu` ouvre le menu principal, et chaque commande ouvre son interface lorsqu'on la tape sans argument.

| Système | Commande | Fonctions |
|---|---|---|
| Nations | `/nation` (`/n`) | Fondation, rangs, invitations, territoire par chunks, carte, alliances, capitale, impôts, protection des blocs et explosions, PvP entre alliés désactivé |
| Recherche & Développement | `/recherche` (`/rd`) | 25 technologies (`research.yml`) payées par le trésor national ; elles débloquent les recettes de la Station |
| Guerres | `/guerre` | Déclaration, préparation, score (kills, captures de chunks, missiles, véhicules), paix, capitulation, réparations |
| Banque | `/banque`, `/payer` | Comptes, billets physiques, virements, historique, intérêts, banque nationale, classements |
| Chambres | `/chambre` ou guichet de réception | Location ou achat, colocataires, revente, restauration automatique de la chambre |
| Armes à feu | — | Pistolet, PM, fusil d'assaut, fusil à pompe, sniper, RPG (`weapons.yml`) |
| Véhicules | — | Char (canon), avion et hélicoptère (mitrailleuse), tracteur (récolte + charrue), carburant, dégâts |
| Station de Craft | Bloc | Fabrication de tout le contenu (`recipes.yml`, section `station`). Modèle 3D d'un bloc et demi : piliers lumineux, écrans animés, vitres, hologramme tournant des créations du mod |
| Machines | Blocs | Compresseur, générateur à charbon, solaires T1-T3, four électrique, concasseur, interrupteur de sécurité. Le panneau des solaires suit le soleil et se met à plat la nuit |
| Missiles | Lanceur | Tactique, croisière, balistique, nucléaire (zone irradiée) ; tir uniquement en guerre active |
| Fusée | Item | Ergol, compte à rebours, décollage, arrivée en orbite ou sur la Lune (gravité faible) |

**Énergie** : les générateurs alimentent, dans un rayon de 16 blocs, les machines qui ont du travail. Le lanceur de
missiles se recharge uniquement sur le surplus. Les machines chauffent en fonctionnant :

- à **100 °C**, une machine s'arrête et sa température continue de monter ;
- à **150 °C**, elle explose ;
- l'**interrupteur de sécurité** coupe le circuit, manuellement ou automatiquement à 90 °C.

**Contrôles**

| Véhicule ou arme | Commandes |
|---|---|
| Armes | Clic droit (maintenu pour le PM et le fusil d'assaut), **R** pour recharger, accroupi pour viser (zoom au sniper) |
| Char et tracteur | ZQSD |
| Avion | Z/S pour les gaz, la souris dirige |
| Hélicoptère | ZQSD, Espace pour monter, Ctrl pour descendre |
| Tous les véhicules | Clic gauche pour tirer, accroupi pour descendre, accroupi + clic droit (propriétaire) pour ranger |

**Administration** (opérateurs) :

| Commande | Effet |
|---|---|
| `/fnations give <joueur> <item> [n]` | Donner un item |
| `/fnations items` | Interface avec tous les items |
| `/fnations recherche <nation> <techno\|tout>` | Accorder des technologies |
| `/fnations reload` | Recharger la configuration |
| `/banque donner <joueur> <montant>` | Créditer un compte |
| `/chambre creer\|pos1\|pos2\|guichet\|reset\|snapshot\|setspawn\|prix\|supprimer` | Gérer les chambres |

**Fichiers** :
- configuration modifiable dans `config/fnations/` : `config.yml`, `research.yml`, `recipes.yml`, `weapons.yml` ;
- données du monde dans `world/fnations/`.

### Installer le serveur

1. Java 21.
2. Lancez `./tools/build.sh <url-des-mods> <adresse-serveur> [port]`. Le script génère les assets, compile le mod,
   remplit `dist/mods/` (FNations + Fabric API), écrit `dist/manifest.json` et copie les mods dans `server/mods/`.
3. (Optionnel, recommandé pour les chambres) WorldEdit Fabric 7.3.11 dans `server/mods/` (le lien est affiché par le script).
4. Acceptez l'EULA (`server/eula.txt`), puis lancez `server/start.sh` (ou `start.bat`). Le script télécharge lui-même
   le lanceur officiel du serveur Fabric.

Les joueurs ont besoin du mod : c'est le rôle du launcher.

---

## 2. Le launcher FNations

Le launcher est une application Electron : un installateur `.exe` pour Windows, une `.dmg` pour macOS et une
AppImage pour Linux.

| Fonction | Détails |
|---|---|
| Connexion Microsoft officielle | OAuth 2.0 avec PKCE dans une fenêtre Microsoft, ou code d'appareil (`microsoft.com/link`). Puis Xbox Live → XSTS → services Minecraft, avec vérification de la licence et du profil. Le launcher ne voit jamais le mot de passe |
| Sessions | Le jeton de rafraîchissement est chiffré par le système (DPAPI, Trousseau ou libsecret via `safeStorage`) et renouvelé automatiquement. Plusieurs comptes sont possibles |
| Installation automatique | Minecraft 1.21.4, Fabric Loader, et le **Java 21 officiel de Mojang** : le joueur n'a rien à installer. Tous les fichiers sont vérifiés par SHA-1 |
| Modpack synchronisé | Le manifeste JSON du serveur liste les mods avec leur SHA-1. Les mods en trop sont retirés, les chemins et les URL sont validés |
| Lancement direct | Connexion automatique au serveur (Quick Play), mémoire et résolution réglables, console du jeu, état du serveur (ping, joueurs en ligne), actualités |

### Étape indispensable : votre application Azure

Le launcher a besoin de **votre propre** identifiant d'application Microsoft :

1. Ouvrez le portail Azure, section **Microsoft Entra ID → Inscriptions d'applications → Nouvelle inscription**.
   - Types de comptes : **comptes Microsoft personnels uniquement** ;
   - URI de redirection : plateforme **Client public/natif (mobile et bureau)**, valeur
     `https://login.microsoftonline.com/common/oauth2/nativeclient`.
2. Dans **Authentification**, activez **Autoriser les flux de clients publics** (nécessaire pour la connexion par code).
3. Copiez l'**ID d'application (client)** dans `launcher/config/launcher.json` (`azureClientId`).
   Aucun secret client n'est nécessaire.
4. **Demandez l'accès à l'API Minecraft** à Mojang avec cet ID, via le formulaire de validation des applications tierces
   (<https://aka.ms/mce-reviewappid>).
   - Tant que la demande n'est pas acceptée, la connexion s'arrête à l'étape Minecraft. Le launcher affiche alors
     un message clair (erreur `app_not_allowed`).
   - Les étapes Microsoft et Xbox fonctionnent déjà.

### Configuration et publication

`launcher/config/launcher.json` contient :
- `azureClientId` ;
- `manifestUrl` : l'URL publique de `dist/manifest.json` ;
- `serverName`, `serverAddress`, `serverPort`, `website`, `discord`.

Un fichier `fnations-launcher.json` placé dans le dossier utilisateur du launcher peut surcharger ces valeurs sans
recompiler.

Hébergez `dist/mods/*` à l'URL donnée à `build.sh` et `dist/manifest.json` à l'URL `manifestUrl` (un hébergement
statique HTTPS suffit). Le manifeste accepte aussi une liste `news` pour les actualités.

```bash
cd launcher
npm install
npm start          # lancer en développement
npm test           # tests unitaires et d'intégration
npm run dist:win   # installateur Windows (ou dist:mac / dist:linux)
```

---

## 3. Développement

```bash
python3 tools/generate_resourcepack.py   # régénère modèles, textures, blockstates et langues du mod
cd mod
./gradlew build                          # -> mod/build/libs/fnations-2.0.0.jar
./gradlew runServer                      # serveur de développement (run-server/)
./gradlew runClient                      # client de développement (run-client/)
./gradlew runAutotest                    # client piloté par un scénario de test (captures dans run-client/screenshots)
FN_SCENARIO=showcase ./gradlew runAutotest   # captures rapprochées des panneaux solaires et de la Station
```

Le mod `autotest` (`mod/src/autotest`) n'est jamais inclus dans le jar publié. Il se connecte à `localhost:25565`,
joue les systèmes et prend des captures. Les modèles 3D sont générés par script ; vous pouvez les retoucher dans
**Blockbench** (`mod/src/main/resources/assets/fnations/models`).

## 4. État des tests

**Mod.** Testé avec un vrai serveur Fabric 1.21.4 et un vrai client Minecraft (rendu logiciel sous Xvfb) :

- chargement du mod, des dimensions, des progrès et des configurations ;
- toutes les interfaces : menu, nation, territoire, R&D, guerres, banque, saisie, chambres, Station, machines,
  lanceur, interrupteur, poste de pilotage ;
- rendu 3D des 59 items, des 10 machines, des 4 véhicules, de la fusée et du missile en vol ;
- panneaux solaires 3D capturés à six heures de la journée (suivi du soleil, mise à plat la nuit, T3 lumineux)
  et hologramme de la Station de Craft ;
- fusil : tir, HUD, zoom du sniper ;
- chaîne générateur → compresseur, avec production vérifiée côté serveur ;
- char : montée, conduite sur 13,7 blocs, descente ;
- **chambre créée avec WorldEdit Fabric** (`//pos1 //pos2`, sauvegarde `.schem`) ;
- **missile tiré depuis le lanceur**, avec impact confirmé ;
- **voyage en fusée jusqu'à la dimension Orbite**.

Ces tests m'ont permis de corriger deux problèmes de répartition de l'énergie entre machines.

**Launcher.** Ce qui a été testé ici :

- 15 tests automatisés :
  - la chaîne Microsoft → Xbox → XSTS → Minecraft, avec des réponses simulées et tous les cas d'erreur ;
  - le chiffrement et le rafraîchissement des comptes ;
  - les règles des fichiers de version ;
  - la synchronisation du modpack et la protection des chemins ;
  - le ping du serveur ;
- un essai de bout en bout : installation réelle de Minecraft 1.21.4, Fabric, Java 21 Mojang et du modpack, puis
  lancement du jeu, qui s'est **connecté tout seul** au serveur avec le mod de production.

Pas encore vérifié :

- la vraie connexion Microsoft : elle demande votre application Azure validée par Mojang et un compte réel ;
- l'empaquetage Electron (`npm run dist`) : il n'a pas pu être exécuté ici ;
- le lancement sur Windows et macOS (testé uniquement sous Linux) ;
- côté mod : le pilotage réel de l'avion et de l'hélicoptère, une guerre entre deux nations avec captures,
  la surchauffe jusqu'à l'explosion, la récolte du tracteur.
