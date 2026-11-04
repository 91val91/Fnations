# FNations — Mod Fabric + Launcher

Serveur Minecraft **1.21.4** de nations sur une **carte de la Terre au 1/1000** : recherche & développement, guerres, banque,
chambres (WorldEdit), armes à feu, véhicules (char, avion, hélicoptère, tracteur), machines et énergie, missiles,
fusée spatiale (Orbite, Lune), métiers, entreprises, hôtel des ventes, **grades et boutique en ligne**, **LootBoxs** et
**spawners**.

Le projet est maintenant un **vrai mod Fabric** (client + serveur). Il est distribué par un **launcher maison**
qui se connecte avec un **vrai compte Microsoft**, installe Minecraft, Fabric, Java et les mods, puis lance le jeu
directement sur le serveur.

```
Fnations/
├── mod/        Mod Fabric (Java 21, Loom, Mojang mappings) : tous les systèmes, GUIs, modèles, données
├── launcher/   Launcher Electron : connexion Microsoft, installation, synchronisation du modpack, lancement
├── server/     Modèle de serveur Fabric (server.properties, scripts de démarrage)
└── tools/      build.sh (tout construire), generate_resourcepack.py (modèles/textures/langues du mod)
                et earth_data.py (données de la Terre : relief, couverture du sol, pays)
```

> L'ancien plugin Paper a été entièrement porté dans le mod et retiré du dépôt. Il reste disponible dans l'historique git.

---

## 1. Le mod FNations

### Ce qui change par rapport au plugin

| Système | Avant (plugin + resource pack) | Maintenant (mod) |
|---|---|---|
| Items | 59 items (base PAPER + modèle du pack) | 59 vrais items enregistrés, données dans des composants (chargeur, billets, carburant) |
| Machines | Blocs vanilla avec un display par-dessus | 10 vrais blocs orientables avec entité de bloc, compatibles entonnoirs, cassés à la pioche. Panneaux solaires et Station de Craft en 3D animée |
| GUIs | Coffres Bukkit | **Tablette FNations** (touche **M**) : interface moderne dessinée par le client (listes, onglets, champs de saisie, cartes, modèle 3D du joueur), pages envoyées par le serveur. Écrans de machine au même thème |
| Armes | Clic droit, F pour recharger | Tir maintenu pour les armes automatiques, recul de caméra, **R** pour recharger, HUD des munitions, zoom de lunette |
| Véhicules | Armor stands téléportés | Vraies entités avec collisions, conduite fluide simulée par le client (comme les bateaux), rotor animé |
| Missiles, fusée | Item displays | Entités rendues en 3D. La fusée voyage vers les **dimensions** `fnations:orbit` et `fnations:moon` |
| Chambres | WorldEdit obligatoire | WorldEdit Fabric si installé (`.schem`), sinon structures vanilla (`.nbt`) |
| Datapack | À part | Intégré au mod : recette de la Station, progrès, loot tables, dimensions |

### La tablette (touche M)

Toutes les interfaces sont réunies dans une tablette au thème sombre (barre de navigation à gauche) :

| Page | Contenu |
|---|---|
| Accueil | Profil du joueur (modèle 3D), solde, nation, entreprise, résumé des six métiers, raccourcis |
| Mon pays | Sans nation : liste des pays (recherche, adhésion) et création avec **carte du relief** autour du joueur, biome, ressources détectées, cultures. Avec nation : profil (bandeau couleur, étiquettes #Build/#Farm…, description, niveau, puissance, carte du territoire, citoyens), membres, **carte des chunks** sur fond de relief (clic pour revendiquer), diplomatie, paramètres |
| Banque | Compte personnel (billets, virements, historique), trésor national, capital de l'entreprise |
| Entreprise | Création (4 types), employés, salaires, capital, bonus de niveau, paramètres |
| Métiers | Compétences : liste, description, « comment monter », piste des 6 paliers, récompenses, classement |
| Hôtel des ventes | Ventes, offres d'achat, mon espace, espace pays, espace entreprise (voir plus bas) |
| R&D | Catégories, technologies (fiche détaillée), recherche en cours avec progression |
| Guerres | Score des conflits, paix, capitulation, capture, déclaration de guerre |
| Chambres | Réception : filtres par groupe, location, achat, colocataires, téléportation |
| Classements | Fortunes, nations, métiers, entreprises, vendeurs de la semaine |
| Carte du monde | La Terre au 1/1000 : relief, frontières, territoires des nations, capitales et votre position. Molette pour zoomer, glisser pour se déplacer, clic pour épingler un lieu (pays, continent, latitude et longitude, nation propriétaire), clic droit pour revenir sur soi. Liste des nations avec recherche (clic = centrer la carte) |
| Boutique | Onglets Grades, LootBoxs, Dollars, Spawners (boutons vers la boutique en ligne) et Mes avantages : grade et validité, récompenses quotidienne, hebdomadaire et mensuelle, achats livrés |

La Station de Craft ouvre aussi sa page (catégories, recettes, ingrédients, fabrication ×1/×5). Ouvrir une LootBox
affiche la **roulette** animée, et un clic droit sur un spawner ouvre sa page (niveau, amélioration, récupération).

### Systèmes

Commandes : `/menu` (ou la touche **M**) ouvre la tablette, et chaque commande ouvre sa page lorsqu'on la tape sans argument.

| Système | Commande | Fonctions |
|---|---|---|
| Nations | `/nation` (`/n`) | Fondation, rangs, invitations, territoire par chunks, carte, alliances, capitale, impôts, protection des blocs et explosions, PvP entre alliés désactivé |
| Recherche & Développement | `/recherche` (`/rd`) | 25 technologies (`research.yml`) payées par le trésor national ; elles débloquent les recettes de la Station |
| Guerres | `/guerre` | Déclaration, préparation, score (kills, captures de chunks, missiles, véhicules), paix, capitulation, réparations |
| Banque | `/banque`, `/payer` | Comptes, billets physiques, virements, historique, intérêts, banque nationale, classements |
| Chambres | `/chambre` ou guichet de réception | Location ou achat, colocataires, revente, restauration automatique de la chambre |
| Métiers | `/metier` (`/jobs`) | Builder, Agriculteur, Chasseur, Bûcheron, Ingénieur, Mineur : XP et argent par action, 6 paliers avec récompenses et bonus (`metiers.yml`) |
| Entreprises | `/entreprise` | Trading, Ingénierie, Agriculture, Construction : niveaux, bonus, salaires horaires, contribution des employés, capital |
| Hôtel des ventes | `/hdv`, `/hdv vendre <prix> [n]` | Ventes groupées par objet, achat au meilleur prix, offres d'achat, boîte de réception, ventes au nom d'une nation ou d'une entreprise, taxe, classement hebdomadaire |
| Classements | `/classement` | Fortunes, nations, métiers, entreprises, HDV |
| Carte du monde | `/carte` (`/monde`) | La Terre au 1/1000 (voir plus bas), avec les territoires des nations |
| Boutique | `/boutique` (`/store`, `/shop`) | Grades, LootBoxs, dollars et spawners achetés en ligne et livrés en jeu, même hors ligne |
| Grades | `/grade` | Premium, Super Premium, Ultra : préfixe, bonus de métier, avantages HDV, récompenses |
| LootBoxs | `/lootbox` ou clic droit | Classic, Légendaire, Ultra : roulette, contenu et chances visibles (`lootboxs.yml`) |
| Spawners | Bloc | 15 mobs, 5 niveaux, amélioration payée en dollars, récupération par le propriétaire (`spawners.yml`) |
| Armes à feu | — | Pistolet, PM, fusil d'assaut, fusil à pompe, sniper, RPG (`weapons.yml`) |
| Véhicules | — | Char (canon), avion et hélicoptère (mitrailleuse), tracteur (récolte + charrue), carburant, dégâts |
| Station de Craft | Bloc | Fabrication de tout le contenu (`recipes.yml`, section `station`). Modèle 3D d'un bloc et demi : piliers lumineux, écrans animés, vitres, hologramme tournant des créations du mod |
| Machines | Blocs | Compresseur, générateur à charbon, solaires T1-T3, four électrique, concasseur, interrupteur de sécurité. Le panneau des solaires suit le soleil et se met à plat la nuit |
| Missiles | Lanceur | Tactique, croisière, balistique, nucléaire (zone irradiée) ; tir uniquement en guerre active |
| Fusée | Item | Ergol, compte à rebours, décollage, arrivée en orbite ou sur la Lune (gravité faible) |

**Métiers** : chaque joueur progresse dans les six métiers à la fois.

- L'argent gagné est versé chaque minute, sans spammer l'historique bancaire. La barre d'action affiche l'XP gagnée.
- Anti-abus : les blocs posés par un joueur ne rapportent rien au Mineur, au Bûcheron ni à l'Agriculteur. Le Builder n'est payé qu'une fois par emplacement. En créatif, aucun métier ne progresse.

| Métier | Gagne de l'XP en… | Bonus des paliers |
|---|---|---|
| Builder | posant des blocs | +0,5 bloc de portée par palier, jusqu'à 12 % de blocs non consommés |
| Agriculteur | récoltant des cultures mûres | jusqu'à 30 % de double récolte, replantation automatique dès le palier 2 |
| Chasseur | tuant monstres et animaux | primes +10 % par palier, orbes d'XP, Force puis Régénération après un kill |
| Bûcheron | coupant des bûches, plantant des pousses | double coupe, abattage de l'arbre entier (accroupi, hache), replantation |
| Ingénieur | fabriquant à la Station, posant des machines | 4 % par palier de chance de récupérer un ingrédient |
| Mineur | minant pierre et minerais naturels | jusqu'à 30 % de double minerai, Célérité I puis II |

**Entreprises** :

- Fonder une entreprise coûte 10 000 $. Sauf pour le Trading, il faut aussi le palier 1 dans le métier associé.
- Les employés font monter son niveau (10 niveaux). Pour le Trading, ce sont les ventes à l'HDV.
- À chaque niveau, les employés gagnent +1 % d'XP et +2 % de revenus dans le métier de l'entreprise.
  - Ingénierie : ingrédients récupérés plus souvent.
  - Trading : taxe HDV réduite, emplacements de vente en plus, ventes au nom de l'entreprise.
- Le PDG fixe la part des revenus de métier reversée au capital (0 à 30 %) et les salaires horaires de chaque employé.

**Hôtel des ventes** :

- Les annonces sont regroupées par objet et classées automatiquement dans 8 catégories et leurs sous-catégories.
- Achat au meilleur prix, sur plusieurs vendeurs si nécessaire.
- Les offres d'achat bloquent la somme jusqu'à la livraison. Un vendeur peut y répondre directement depuis son inventaire.
- Les objets achetés qui ne tiennent pas dans l'inventaire et les annonces expirées (7 jours) arrivent dans la boîte de réception.
- Taxe de 5 % sur le vendeur.
- Un Ministre peut vendre au nom de sa nation (recette versée au trésor), un Directeur d'entreprise de Trading au nom de son entreprise.

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

### La Terre au 1/1000

Le monde principal est la Terre : **1 bloc = 1 km**. X suit la longitude, Z la latitude (le nord est vers −Z) : le monde
mesure 40 075 × 20 037 blocs et une bordure est posée automatiquement.

- **Relief réel** (ETOPO5, NOAA) : le niveau de la mer est à y 63, les plus hauts sommets de l'Himalaya montent vers
  y 190 et les fosses océaniques descendent vers y −38. Une case de données couvre 9 km : le relief y est moyenné,
  puis interpolé et enrichi d'un bruit de détail.
- **Biomes** tirés de la couverture du sol (Natural Earth II) : forêts, steppes, savanes, déserts, toundra, calottes
  de glace. Les océans dépendent de la latitude et de la profondeur. Plages, mangroves, marais, sommets enneigés,
  cerisiers au Japon. Les villages et structures vanilla sont générés normalement.
- **Pays** (Natural Earth) : la tablette indique le pays et le continent sous vos pieds (création de nation, carte).
- **Activation** : `level-type=fnations\:earth` dans `server.properties` avant la création du monde (déjà réglé dans
  `server/server.properties`). En solo, choisissez le type de monde « Terre FNations (1/1000) ».
- **Apparition** à Paris par défaut (`carte.apparition-latitude` et `carte.apparition-longitude` dans `config.yml`).
- Le Nether et l'End restent vanilla.

Les données sont régénérées par `python3 tools/earth_data.py` (téléchargement de données du domaine public).

### Boutique en ligne : grades, LootBoxs, dollars, spawners

Les achats se font sur une boutique web (Tebex conseillé). Chaque article exécute une commande console `fnstore`.
Si le joueur est hors ligne, la livraison attend sa prochaine connexion (`world/fnations/boutique.yml`).

| Article | Commande à mettre dans l'article Tebex |
|---|---|
| Grade (jours facultatifs, permanent sinon) | `fnstore grade {username} ultra 30` |
| LootBoxs | `fnstore lootbox {username} legendaire 3` |
| Dollars (crédités sur le compte bancaire) | `fnstore dollars {username} 100000` |
| Spawner (type, niveau, nombre) | `fnstore spawner {username} blaze 1 1` |

**Brancher Tebex** :

1. Créez la boutique et ajoutez un serveur de jeu Minecraft avec l'intégration par plugin.
2. Copiez sa **clé secrète** dans `config/fnations/config.yml`, dans `boutique.tebex-cle-secrete`.
3. Le serveur interroge l'API Tebex toutes les `tebex-intervalle-secondes`. Il exécute les commandes en attente :
   celles « hors ligne » tout de suite, celles « en ligne » quand le joueur est connecté. Puis il les confirme à Tebex.
4. Mettez l'adresse de la boutique dans `boutique.url`, et les prix affichés dans `boutique.prix`, `boutique.dollars` et
   `boutique.spawners`. Dans la tablette, les boutons ouvrent la boutique après la confirmation de lien de Minecraft.

Sans Tebex, n'importe quelle boutique capable d'envoyer une commande console (RCON, panel) fonctionne avec les mêmes
commandes.

**Grades** : racheter le même grade prolonge sa durée, et un grade inférieur ne remplace jamais un grade supérieur actif.

| | Premium | Super Premium | Ultra |
|---|---|---|---|
| Préfixe (chat, liste des joueurs) | `[Premium]` doré | `[Super Premium]` bleu | `[Ultra]` violet |
| Revenus de métier | +5 % | +10 % | +15 % |
| Emplacements de vente HDV | +5 | +10 | +20 |
| Taxe HDV | −0,5 point | −1 point | −2 points |
| Récompense quotidienne | 500 $ | 1 500 $ | 4 000 $ |
| Récompense hebdomadaire | LootBox Classic | LootBox Classic | LootBox Légendaire |
| Récompense mensuelle | — | LootBox Légendaire | LootBox Ultra |

**LootBoxs** :

- Trois boîtes : Classic, Légendaire et Ultra. Leur contenu se règle dans `lootboxs.yml` : argent, objets, spawners,
  jours de grade, autres LootBoxs, chacun avec un poids et une rareté.
- On ouvre une boîte d'un clic droit ou depuis la tablette : une roulette de 6 secondes ralentit jusqu'au gain.
- Le gain est tiré par le serveur à l'ouverture, puis livré à la fin de la roulette (ou tout de suite si le joueur se
  déconnecte). Les gains légendaires et mythiques sont annoncés à tout le serveur.

**Spawners** :

- 15 types de mobs et 5 niveaux, réglés dans `spawners.yml` : délai, mobs par vague, maximum autour, coût
  d'amélioration (15 000 $ à 200 000 $).
- Un spawner fonctionne quand un joueur est à moins de 16 blocs.
- Seul le propriétaire (ou un opérateur) peut l'améliorer, le récupérer ou le casser. Il garde son mob et son niveau,
  même cassé à la pioche.
- Les mobs de spawner rapportent moitié moins au métier de Chasseur.

**Administration** (opérateurs) :

| Commande | Effet |
|---|---|
| `/fnations give <joueur> <item> [n]` | Donner un item |
| `/fnations items` | Interface avec tous les items |
| `/fnations recherche <nation> <techno\|tout>` | Accorder des technologies |
| `/fnations reload` | Recharger la configuration |
| `/metier set <joueur> <métier> <xp>` | Définir l'XP d'un métier |
| `/banque donner <joueur> <montant>` | Créditer un compte |
| `fnstore <grade\|lootbox\|dollars\|spawner> <joueur> [arguments]` | Livrer un achat de la boutique (console, niveau 3) |
| `/chambre creer\|pos1\|pos2\|guichet\|reset\|snapshot\|setspawn\|prix\|supprimer` | Gérer les chambres |

**Fichiers** :
- configuration modifiable dans `config/fnations/` :
  - `config.yml`, avec les sections `entreprises` et `hdv` ;
  - `research.yml`, `recipes.yml`, `weapons.yml` ;
  - `metiers.yml` : paliers, récompenses, XP et argent de chaque action ;
  - `lootboxs.yml` (contenu des LootBoxs) et `spawners.yml` (mobs et niveaux) ;
- données du monde dans `world/fnations/` :
  - `nations.yml`, `bank.yml` ;
  - `jobs.yml`, `entreprises.yml` ;
  - `hdv.json`, qui contient les objets en vente avec tous leurs composants ;
  - `grades.yml` (grades et récompenses récupérées), `boutique.yml` (livraisons en attente et historique),
    `terre.yml`.

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
FN_SCENARIO=terre ./gradlew runAutotest      # survol de lieux réels (Paris, Alpes, Himalaya, Sahara...)
FN_SCENARIO=boutique ./gradlew runAutotest   # livraisons, grades, LootBoxs, spawner, carte du monde
python3 tools/earth_data.py                  # régénère les données de la Terre (mod/src/main/resources/fnations-earth)
```

Le mod `autotest` (`mod/src/autotest`) n'est jamais inclus dans le jar publié. Il se connecte à `localhost:25565`,
joue les systèmes et prend des captures. Les modèles 3D sont générés par script ; vous pouvez les retoucher dans
**Blockbench** (`mod/src/main/resources/assets/fnations/models`).

## 4. État des tests

**Mod.** Testé avec un vrai serveur Fabric 1.21.4 et un vrai client Minecraft (rendu logiciel sous Xvfb) :

- chargement du mod, des dimensions, des progrès et des configurations ;
- toutes les pages de la tablette, chacune capturée et pilotée depuis le client :
  - accueil ;
  - création du pays (carte du relief, biome, ressources), profil, membres ;
  - territoire avec revendication d'un chunk par clic sur la carte ;
  - banque (retrait d'un billet) ;
  - métiers avec classement ;
  - création d'entreprise ;
  - HDV ;
  - R&D, guerres, chambres, Station de Craft (fabrication d'un lingot d'acier), classements ;
- métiers : 5 blocs posés en survie donnent 5 XP de Builder, et les paliers versent leurs récompenses ;
- hôtel des ventes :
  - mise en vente de 4 objets, dont des flèches au nom de la nation ;
  - achat de 10 flèches au meilleur prix, payé au trésor national taxe déduite (38 $ pour 40 $) ;
  - offre d'achat avec somme bloquée ;
  - onglets Mon espace et Espace pays, filtres par catégorie ;
- écrans de machine (au thème de la tablette) : générateur, compresseur, lanceur, interrupteur, poste de pilotage ;
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

**Terre, boutique, LootBoxs et spawners** (scénarios `terre` et `boutique`, même vrai serveur et vrai client) :

- génération de la Terre vérifiée sur place : Paris (avec un village vanilla), Alpes, Himalaya, Sahara, Amazonie,
  Groenland, mont Fuji, détroit de Gibraltar, Australie ;
- carte du monde : vue d'ensemble, zoom sur l'Europe puis sur Paris, territoire et capitale de la nation, épingle,
  recherche de nation ;
- création d'un pays à Paris : la tablette affiche « France • Europe » et les coordonnées ;
- livraisons `fnstore` :
  - grade Ultra, avec le préfixe dans le chat et la liste des joueurs ;
  - 5 LootBoxs, 250 000 $ et un spawner à blaze ;
  - une livraison mise en attente pour un joueur hors ligne ;
- onglets de la boutique, récompense quotidienne, lien vers la boutique (confirmation de Minecraft puis retour à la
  tablette) ;
- 3 LootBoxs ouvertes, depuis la tablette et par clic droit, avec la roulette capturée pendant et après l'arrêt ;
- spawner posé, amélioré au niveau 3, mobs apparus autour, puis récupéré avec son niveau ;
- un `config.yml` d'une ancienne version reçoit automatiquement les sections ajoutées.

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
  la surchauffe jusqu'à l'explosion, la récolte du tracteur ;
- la récupération des achats par l'API Tebex : elle demande une vraie boutique et sa clé secrète. La livraison
  elle-même (`fnstore`) est testée.
