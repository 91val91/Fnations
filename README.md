# FNations — Serveur Minecraft de Nations

Serveur **Paper 1.21.4** complet : nations, recherche & développement, guerres, banque, chambres (WorldEdit),
armes à feu, véhicules (char, avion, hélicoptère, tracteur), machines, missiles et fusée spatiale, tout avec des GUIs.

> **Aucun mod à installer côté joueur.** Le contenu « moddé » est fait par un **plugin** (toute la logique)
> et un **resource pack** (les modèles 3D, imposé automatiquement à la connexion). Un **datapack** ajoute
> les recettes vanilla et les progrès.

```
Fnations/
├── plugin/        Plugin Paper (Java 21, Gradle) : source de tous les systèmes et GUIs
├── resourcepack/  Resource pack généré (modèles 3D + textures)
├── datapack/      Datapack « fnations » (recette de la Station, progrès, fonctions)
├── server/        server.properties, scripts de lancement
└── tools/         build.sh (tout construire) et generate_resourcepack.py (régénère les modèles)
```

---

## Installation

1. **Java 21** et **Paper 1.21.4** : téléchargez `paper.jar` sur <https://papermc.io/downloads/paper> et placez-le dans `server/`.
2. **WorldEdit 7.3.x** (obligatoire, utilisé par les chambres) : téléchargez la version Paper 1.21.4 de WorldEdit
   (les versions 7.4.x demandent Java 25) et placez-la dans `server/plugins/`.
3. Construisez le projet :
   ```bash
   ./tools/build.sh
   ```
   Le script produit dans `dist/` : `FNations-1.0.0.jar`, `FNations-RP.zip` (resource pack + SHA-1 affiché),
   `FNations-datapack.zip`, et les copie dans `server/plugins` et `server/world/datapacks`.
4. **Resource pack** : hébergez `dist/FNations-RP.zip` (lien direct, par exemple GitHub Release ou mc-packs.net), puis
   renseignez `resource-pack=` et `resource-pack-sha1=` dans `server/server.properties`.
5. Acceptez l'EULA (`server/eula.txt`), puis lancez `server/start.sh` (Linux/macOS) ou `start.bat` (Windows).

Premiers pas d'un joueur : `/menu`. Une Station de Craft se fabrique à la table de craft
(blocs de fer, redstone, table de craft et table de forgeron) ou via `/function fnations:give_station` pour un admin.

---

## Les systèmes

| Système | Commande / accès | GUI |
|---|---|---|
| Menu principal | `/menu` | Accès à tout |
| Nations | `/nation` (`/n`) | Membres, carte du territoire, diplomatie, trésor, paramètres |
| Recherche & Développement | `/recherche` (`/rd`) | Arbre technologique en 6 catégories |
| Guerres | `/guerre` | Déclaration, score, captures, paix, capitulation |
| Banque / Banque nationale | `/banque`, `/payer` | Dépôts, billets, virements, historique, classements |
| Chambres | `/chambre`, ou clic droit sur un guichet | Réception : louer, acheter, colocataires, revendre |
| Station de Craft | Clic droit sur le bloc | Armes, munitions, véhicules, machines, missiles, fusée |
| Machines | Clic droit sur la machine | Entrée/sortie, jauges d'énergie et de chaleur |

### Nations
Création payante, rangs (Chef d'État, Ministre, Citoyen), invitations ou recrutement ouvert, territoire par chunks
(adjacents, limite selon membres et recherches), protection des blocs et des explosions, alliances, capitale,
impôt horaire versé au trésor, et PvP désactivé entre membres et alliés. La carte du territoire est un GUI 9×5 coloré.

### Recherche & Développement (`research.yml`)
25 technologies en 6 branches : Économie, Industrie, Armement, Véhicules, Missiles, Spatial. Chaque recherche est
payée par le trésor national, prend un temps réel et a des prérequis. Elle **débloque les recettes** de la Station de
Craft (et des chunks bonus pour Expansion). Une seule recherche à la fois par nation, annulation remboursée à 50 %.

### Guerres
La déclaration demande la technologie *Doctrine militaire*, coûte de l'argent et lance une phase de préparation de 10 min.
Une fois la guerre active :
- un kill rapporte +5 points et une prime ;
- une capture rapporte +15 : rester 60 s sur un chunk ennemi (avec bossbar), le chunk change alors de nation ;
- une frappe de missile rapporte +10 (×3 en nucléaire), un véhicule détruit +8.

La guerre se termine au score de victoire (100), par paix blanche, par capitulation, à la durée maximale, ou
quand le vaincu perd tout son territoire. Le vaincu paie 30 % de son trésor en réparations.
Pendant une guerre active, le territoire ennemi peut être modifié.

### Chambres (WorldEdit)
Côté admin :
```
//wand  (sélectionnez la chambre)
/chambre creer <id> <prixAchat|0> <prixLocationJour|0> [groupe]
/chambre guichet [groupe]      ← en regardant un bloc (pupitre, panneau...) : il devient la réception
/chambre setspawn|prix|reset|snapshot|supprimer|tp|liste
```
À la création, l'état de la chambre est sauvegardé en **schematic WorldEdit** (`plugins/FNations/rooms/<id>.schem`).
Quand la location expire ou que la chambre est libérée, WorldEdit **restaure** cet état. Seuls le propriétaire et
ses colocataires peuvent construire ou ouvrir des portes dans la chambre.

### Banque
Compte personnel avec argent de départ, intérêts horaires plafonnés et historique. Les **billets physiques**
se retirent en GUI et se redéposent au clic droit. Il y a aussi les virements, le classement des fortunes et la
**banque nationale** : tout citoyen peut y verser, seuls les Ministres peuvent retirer.

### Armes à feu (`weapons.yml`)
Six armes : pistolet, PM, fusil d'assaut, fusil à pompe (8 plombs), sniper (zoom en visée) et RPG (roquette explosive).
- **Clic droit** : tirer ;
- **F** : recharger ;
- **Accroupi** : viser, ce qui réduit la dispersion.

Le chargeur est stocké dans l'item, les headshots sont multipliés, et les munitions se fabriquent à la Station.

### Véhicules (`config.yml > vehicules`)
| Véhicule | Contrôles | Arme |
|---|---|---|
| Char | ZQSD (rotation de la caisse) | Canon (obus de char), clic gauche |
| Avion | Z/S = gaz, direction = regard, décollage à vitesse minimale, décrochage | Mitrailleuse |
| Hélicoptère | ZQSD, Espace = monter, Ctrl = descendre, rotor animé, 4 places | Mitrailleuse |
| Tracteur | ZQSD, Espace = charrue (laboure) ; récolte et replante les cultures mûres | — |

Les véhicules consomment du carburant (clic droit avec un **Jerrican**) et ont des points de vie : ils subissent les
balles, roquettes, obus, missiles et crashs. Clic droit pour monter, accroupi pour descendre,
accroupi + clic droit (propriétaire) pour ranger. Ils sont sauvegardés au redémarrage.

### Machines & énergie
Les machines se posent comme des blocs, avec un modèle 3D par-dessus :

| Machine | Fonction |
|---|---|
| **Générateur à charbon** | 60 E/s, brûle charbon / bloc de charbon / blaze rod |
| **Panneaux solaires T1/T2/T3** | 10 / 30 / 80 E/s le jour ; le T3 produit 25 % la nuit, la pluie divise par deux |
| **Compresseur** | Fer → fer compressé, acier → plaque d'acier, charbon → plaque de carbone, cuivre → fil |
| **Four électrique** | Toutes les recettes de four vanilla en 3 s, plus fer compressé → acier et poudres → lingots |
| **Concasseur** | Minerais → 2 poudres. **Recycle les missiles** en composants (le missile nucléaire rend son cœur) |
| **Interrupteur de sécurité** | Coupe toutes les machines dans un rayon de 16 blocs ; mode disjoncteur automatique à 90 °C |

L'énergie circule automatiquement entre générateurs et machines dans un **rayon de 16 blocs**.
**Surchauffe** : les machines chauffent en travaillant. À 100 °C elles s'arrêtent et la chaleur continue de monter
tant que le circuit n'est pas coupé par l'interrupteur. À 150 °C, la machine **explose**.

### Missiles (`config.yml > missiles`)
Le **lanceur de missiles** se charge en énergie depuis le réseau. On place le missile dans son GUI, on saisit la
cible « X Z », puis on lance : compte à rebours, trajectoire balistique visible et impact.

| Missile | Portée |
|---|---|
| Tactique | 400 blocs |
| Croisière | 2 000 blocs |
| Balistique | 6 000 blocs, explosions secondaires |
| **Nucléaire** | 12 000 blocs, 16 explosions, zone irradiée 10 min (poison, nausée) |

Un tir sur une autre nation n'est autorisé qu'en **guerre active** contre elle, et ses défenseurs reçoivent une alerte.

### Fusée
Fabriquée à la Station (technologie *Conquête spatiale*), posée au sol, remplie avec 16 **Ergols de fusée**.
Le pilote choisit sa destination dans le GUI de bord : **Station orbitale** (monde vide avec plateforme),
**Lune** (monde lunaire, gravité faible) ou **Retour sur Terre**. Ensuite viennent un compte à rebours de 10 s,
le décollage avec flammes et fumée, puis l'arrivée dans l'autre monde, qui est créé automatiquement.

---

## Administration

| Commande | Effet |
|---|---|
| `/fnations give <joueur> <item> [n]` | Donner un item (auto-complétion des 59 items) |
| `/fnations items` | GUI de tous les items |
| `/fnations recherche <nation> <techno\|tout>` | Accorder des technologies |
| `/fnations reload` / `save` | Recharger les YAML / sauvegarder |
| `/banque donner <joueur> <montant>` | Créditer un compte |

Permissions : `fnations.admin` (op), `fnations.admin.bypass` (ignore protections et recherches),
`fnations.station.distance` (`/station` sans bloc).

Fichiers de configuration (dans `plugins/FNations/`) :

| Fichier | Contenu |
|---|---|
| `config.yml` | Économie, nations, guerres, chambres, véhicules, machines, missiles, fusée |
| `items.yml` | Items custom |
| `research.yml` | Arbre technologique |
| `recipes.yml` | Station et machines |
| `weapons.yml` | Armes |

Les données sont dans `plugins/FNations/data/`.

---

## Resource pack & modèles

`tools/generate_resourcepack.py` génère le pack : les icônes sont en pixel-art 16×16 et les modèles 3D sont faits en
éléments JSON. Les items utilisent le composant `item_model` (`fnations:<id>`). Vous pouvez ouvrir et retoucher
n'importe quel modèle (`resourcepack/FNations-RP/assets/fnations/models/item/*.json`) dans **Blockbench**.
Si un véhicule apparaît tourné dans le mauvais sens, ajustez `modele-rotation` (0 ou 180) dans `config.yml`.
`echelle` et `modele-y` règlent la taille et la hauteur.

## Développement

```bash
cd plugin && gradle build                 # → plugin/build/libs/FNations-1.0.0.jar
python3 tools/generate_resourcepack.py    # régénère le resource pack
```

## État des tests

Testé sur un vrai serveur Paper 1.21.4 (build 232) avec WorldEdit 7.3.19, piloté par un bot (mineflayer).
Les scénarios suivants fonctionnent, sans exception dans la console :
- chargement du plugin et du datapack ;
- création de nation, claim, banque, R&D ;
- ouverture de tous les GUIs ;
- création d'une chambre avec sauvegarde WorldEdit ;
- tir au sniper (mob tué, chargeur décrémenté) ;
- déploiement, ravitaillement, conduite (le char avance d'environ 20 blocs), tir au canon et descente du char ;
- production d'un compresseur alimenté par un générateur à charbon ;
- lancement complet d'un missile jusqu'à l'impact.

Ce qui reste à vérifier avec un vrai client :
- **le rendu visuel du resource pack** (modèles et positions en main) ;
- les vols d'avion et d'hélicoptère ;
- la fusée ;
- la surchauffe jusqu'à l'explosion ;
- une guerre entre deux nations, avec captures.
