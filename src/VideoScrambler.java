import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;

/**
 * Classe utilitaire pour gérer le chiffrement (mélange de lignes)
 * et la cryptanalyse (brute-force) d'images vidéo.
 */
public class VideoScrambler {

    /**
     * Génère la table de permutation pour une hauteur donnée et une clé (r, s).
     * @param height Hauteur de l'image
     * @param r Composante r de la clé (offset)
     * @param s Composante s de la clé (step)
     * @param inverse Si true, génère la table inverse pour le déchiffrement
     * @return Tableau d'entiers représentant la permutation
     */
    public static int[] getPermutationMap(int height, int r, int s, boolean inverse) {
        int[] map = new int[height];
        // Initialisation de l'identité
        for (int i = 0; i < height; i++) map[i] = i;

        // Application récursive du mélange par blocs de puissance de 2
        applyRecursivePermutation(map, 0, height, r, s);

        // Si on veut déchiffrer, on inverse la map : dst[map[i]] = i
        if (inverse) {
            int[] invMap = new int[height];
            for (int i = 0; i < height; i++) {
                invMap[map[i]] = i;
            }
            return invMap;
        }
        return map;
    }

    /**
     * Méthode récursive pour calculer les indices de destination.
     */
    private static void applyRecursivePermutation(int[] map, int startIdx, int length, int r, int s) {
        if (length <= 0) return;

        // Trouver la plus grande puissance de 2 <= length
        int p2 = Integer.highestOneBit(length);

        // Création d'un buffer temporaire pour ce bloc
        int[] tempBlock = new int[p2];
        for(int i=0; i<p2; i++) {
            tempBlock[i] = map[startIdx + i];
        }

        // Appliquer la formule : pos = (r + (2s+1)id) % size
        // Ici, on remplace map[start + id] par ce qui vient de tempBlock
        // Attention : la consigne dit "La ligne idLigne... sera diffusée en position..."
        // Cela signifie : Destination[newPos] = Source[oldPos]

        int[] permutatedBlock = new int[p2];

        for (int i = 0; i < p2; i++) {
            // Formule du sujet
            long destIndexLong = (r + (2L * s + 1) * i) % p2;
            int destIndex = (int) destIndexLong;

            // On place l'index d'origine 'i' à la nouvelle position 'destIndex'
            // permutatedBlock[destIndex] correspondra à la ligne qui atterrit là
            permutatedBlock[destIndex] = tempBlock[i];
        }

        // Copie du bloc permuté dans la map globale
        for(int i=0; i<p2; i++) {
            map[startIdx + i] = permutatedBlock[i];
        }

        // Appel récursif pour le reste des lignes
        applyRecursivePermutation(map, startIdx + p2, length - p2, r, s);
    }

    /**
     * Applique la permutation (ou l'inverse) sur une image Mat.
     * @param src Image source
     * @param r Clé r
     * @param s Clé s
     * @param reverse false pour chiffrer, true pour déchiffrer
     * @return Nouvelle Mat traitée
     */
    public static Mat processImage(Mat src, int r, int s, boolean reverse) {
        int height = src.height();
        int width = src.width();
        Mat dst = new Mat(src.size(), src.type());

        // Récupérer la map (si reverse=true, getPermutationMap renvoie déjà l'inverse)
        int[] map = getPermutationMap(height, r, s, reverse);

        // Pour optimiser, on pourrait manipuler les buffers de données brutes,
        // mais copier row par row est plus sûr via l'API OpenCV Java.
        for (int i = 0; i < height; i++) {
            int sourceRowIndex = reverse ? map[i] : i;
            int destRowIndex = reverse ? i : map[i];

            // Si reverse est true (déchiffrement), map[i] contient l'origine de la ligne i
            // Si reverse est false (chiffrement), map[i] contient la destination de la ligne i

            // Approche simplifiée :
            // Map contient : pour la ligne i de l'image 'src', elle va à la ligne map[i] de 'dst'
            // SAUF que getPermutationMap avec inverse=true inverse la logique.

            // Utilisons une logique unifiée :
            // dst.row(i) = src.row(source_qui_va_en_i)
            // C'est plus simple de le voir comme :
            // chiffrer : dst.row(map[i]) = src.row(i)

            Mat srcRow = src.row(i);
            Mat dstRow = dst.row(map[i]);
            srcRow.copyTo(dstRow);
        }
        return dst;
    }

    /**
     * Tente de trouver la clé (r,s) par force brute (Étape 2).
     * @param scrambledImage Image chiffrée
     * @return Tableau {r, s} optimal
     */
    public static int[] crackKey(Mat scrambledImage) {
        // Optimisation : Travailler sur une image réduite pour aller plus vite
        Mat small = new Mat();
        Imgproc.cvtColor(scrambledImage, scrambledImage, Imgproc.COLOR_BGR2GRAY);
        Imgproc.resize(scrambledImage, small, new Size(640, 360)); // Taille arbitraire plus petite

        int height = small.height();
        int width = small.width();

        double bestScore = Double.MAX_VALUE;
        int bestR = 0;
        int bestS = 0;

        // Pré-allocation pour éviter de créer des objets dans la boucle
        // On ne va pas recréer l'image entière à chaque fois, c'est trop lent.
        // On va juste calculer le score sur les indices permutés.

        // 2^15 = 32768 itérations. C'est faisable.
        for (int s = 0; s < 128; s++) {
            for (int r = 0; r < 256; r++) {
                // Obtenir la map inverse pour cette clé (comment remettre les lignes dans l'ordre)
                int[] invMap = getPermutationMap(height, r, s, true);

                // Calcul du score sans reconstruire l'image Mat :
                // On compare la ligne invMap[i] (qui est la ligne i déchiffrée)
                // avec la ligne invMap[i+1] (ligne i+1 déchiffrée) de l'image brouillée source.
                double currentScore = calculateEuclideanScore(small, invMap);

                if (currentScore < bestScore) {
                    bestScore = currentScore;
                    bestR = r;
                    bestS = s;
                }
            }
        }

        System.out.println("Clé trouvée : R=" + bestR + ", S=" + bestS + " (Score: " + bestScore + ")");
        return new int[]{bestR, bestS};
    }

    /**
     * Calcule la "rugosité" de l'image basée sur la map proposée.
     * Critère : Distance euclidienne entre lignes consécutives.
     */
    private static double calculateEuclideanScore(Mat img, int[] lineOrder) {
        double totalDist = 0;
        int h = img.height();
        int w = img.width();
        int channels = img.channels(); // 3 pour BGR

        // Pour aller très vite, on ne check pas tous les pixels, ou on prend un pas
        // Ici on fait une implémentation simple mais complète sur la largeur
        // Accès direct aux pixels est lent en Java OpenCV via get().
        // Idéalement on convertit en byte[] avant la boucle for, mais img change peu.

        // Optimisation : on prend juste une colonne centrale et deux latérales
        // ou on charge tout en byte array une fois.

        // Pour l'exercice, on va supposer qu'on a converti l'image en buffer avant l'appel
        // Mais comme l'appelant est crackKey, faisons-le ici ou optimisons.
        // On va utiliser un échantillonnage (ex: colonne du milieu) pour la rapidité

        // TEST RAPIDE : Comparer seulement la colonne du milieu (width/2)
        // C'est souvent suffisant pour voir la continuité.

        byte[] b1 = new byte[channels];
        byte[] b2 = new byte[channels];

        int col = w / 2;

        for (int i = 0; i < h - 1; i++) {
            // Ligne i de l'image déchiffrée correspond à la ligne lineOrder[i] de l'image source
            int rowA = lineOrder[i];
            int rowB = lineOrder[i+1];

            img.get(rowA, col, b1);
            img.get(rowB, col, b2);

            double distSq = 0;
            for(int c=0; c<channels; c++) {
                int val1 = b1[c] & 0xFF; // conversion unsigned
                int val2 = b2[c] & 0xFF;
                distSq += (val1 - val2) * (val1 - val2);
            }
            totalDist += distSq; // Pas besoin de racine carrée pour comparer
        }

        return totalDist;
    }
}
