# VideoScrambleApp: Chiffrement et Cryptanalyse Vidéo par Permutation de Lignes

Ce projet, inspiré des anciennes méthodes de chiffrement vidéo analogique (années 80/90), implémente un système de **chiffrement symétrique par permutation de lignes** ainsi qu'une méthode de **cassage de clé par force brute** et évaluation statistique.

Il est développé en **Java** en utilisant **JavaFX** pour l'interface graphique et **OpenCV** pour le traitement vidéo bas niveau.

---

## 🛠️ Prérequis

Pour exécuter ce projet, vous devez disposer des éléments suivants :

* **Java Development Kit (JDK) 17 ou supérieur.**
* **OpenCV 4.x :** La bibliothèque native doit être correctement installée et liée au chemin (`Core.NATIVE_LIBRARY_NAME`).
* **JavaFX SDK :** Les modules JavaFX nécessaires doivent être ajoutés à la configuration de votre IDE (ou inclus via Maven/Gradle si vous utilisez un système de build).

---

## 🚀 Démarrage rapide

### 1. Installation et Compilation

Clonez le dépôt et assurez-vous que toutes les dépendances (JavaFX et OpenCV) sont configurées dans votre environnement de développement (par exemple, dans IntelliJ ou Eclipse).

### 2. Lancement

Exécutez la classe principale : `VideoGrabDemo.java`.

---

## ✨ Fonctionnalités implémentées (Étapes 1 & 2)

### 1. Chiffrement et Déchiffrement Vidéo

Le cœur du projet est la permutation des lignes, basée sur une clé secrète $(r, s)$ :

* **Clé $(r, s)$ :** Composée de $r$ (offset, 8 bits) et $s$ (step, 7 bits), soit $2^{15}$ clés possibles.
* **Algorithme :** La permutation est appliquée de manière **récursive** par blocs décroissants de puissances de 2, permettant de traiter des vidéos de n'importe quelle hauteur ($H$).
* **Interface :** L'IHM permet de charger une vidéo d'entrée (`Load Video File`), de définir la clé $(r, s)$, et de choisir entre les modes `Scramble` (chiffrement) et `Unscramble` (déchiffrement).
* **Sortie :** La vidéo traitée (chiffrée ou déchiffrée) est affichée en temps réel et est simultanément enregistrée sur le disque dur.

**Formule de Permutation (pour un bloc de taille $size = 2^n$) :**
La ligne $idLigne$ est déplacée vers la position :
$$
\text{position}_{\text{dest}} = (r + (2s+1) \cdot idLigne) \pmod{\text{size}}
$$

### 2. Cryptanalyse par Force Brute (Cassage de Clé)

La fonctionnalité `CRACK KEY` permet de retrouver la clé $(r, s)$ sans la connaître, en analysant l'image chiffrée.

* **Méthode :** Le programme essaie les $2^{15} = 32\,768$ combinaisons possibles de clés.
* **Critère de Sélection :** Pour chaque clé testée, l'image est virtuellement déchiffrée, et sa "lisibilité" est évaluée en utilisant un score basé sur la **Distance Euclidienne** entre des paires de lignes consécutives.
* **Heuristique :** Une image claire présente une forte ressemblance entre ses lignes adjacentes (score faible), tandis qu'une image brouillée montre des lignes très différentes (score élevé).
* **Optimisation :** Pour accélérer le processus, le cassage de clé est effectué sur une version redimensionnée (plus petite) de l'image.

---

## ⌨️ Utilisation de l'IHM

| Composant | Description |
| :--- | :--- |
| **Start Camera** | Lance l'acquisition depuis la webcam (si connectée). |
| **Load Video File** | Charge un fichier vidéo pour le traitement. |
| **Key R / Key S** | Champs de texte pour entrer les composantes de la clé (8 bits et 7 bits respectivement). |
| **Mode: Scramble** | Active le chiffrement (mélange des lignes) de la source vers la destination. |
| **Mode: Unscramble** | Active le déchiffrement (démélange des lignes) de la source vers la destination. |
| **CRACK KEY** | Lance l'attaque par force brute sur l'image chiffrée actuellement affichée (côté Source). Le résultat met à jour les champs R et S. |
| **Original Frame / Processed Frame** | Vue côte à côte de la source et du résultat du traitement. |

---

## 🤝 Contributeurs

* BOISSELOT Harry
* VIDY Enzo
* BUT3 S5-A2
