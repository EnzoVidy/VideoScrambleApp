import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.stream.IntStream;

/**
 * Classe utilitaire pour le projet VideoScramble.
 * Gère le chiffrement (mélange de lignes) et la cryptanalyse (brute-force).
 * Scope : Étape 1 et Étape 2 uniquement.
 */
public class VideoScrambler {

    // --- Paramètres de force brute ---
    // Largeur réduite pour l'analyse (suffisant pour la corrélation et très rapide)
    private static final int ANALYSIS_WIDTH = 64;

    // Plages de clés définies par le sujet
    private static final int R_MAX = 256; // 8 bits
    private static final int S_MAX = 128; // 7 bits

    /**
     * Génère la carte de permutation : map[i] = position de la ligne i dans l'image chiffrée.
     * Cette méthode respecte la logique récursive par blocs de puissance de 2.
     * * @param height Hauteur de l'image
     * @param r Offset (clé)
     * @param s Step (clé)
     * @return Tableau d'indices
     */
    public static int[] getPermutationMap(int height, int r, int s) {
        int[] map = new int[height];
        // Initialisation à l'identité (0, 1, 2...)
        for (int i = 0; i < height; i++) map[i] = i;

        int currentStart = 0;
        int remainingLines = height;

        // Traitement itératif des blocs (itération #1, #2, #3...)
        while (remainingLines > 0) {
            // Trouver la plus grande puissance de 2 <= remainingLines
            // Si remainingLines = 1, highestOneBit renvoie 1
            int blockSize = (remainingLines >= 2) ? Integer.highestOneBit(remainingLines) : remainingLines;

            // Appliquer le mélange sur ce bloc spécifique
            permuteBlock(map, currentStart, blockSize, r, s);

            // Avancer au prochain bloc (le résidu)
            currentStart += blockSize;
            remainingLines -= blockSize;
        }
        return map;
    }

    /**
     * Applique la formule de permutation (r + (2s+1)id) % size sur un sous-bloc du tableau.
     */
    private static void permuteBlock(int[] map, int startIdx, int size, int r, int s) {
        if (size <= 1) return; // Pas de mélange possible sur 1 seule ligne

        // On sauvegarde les valeurs actuelles du bloc pour ne pas écraser les données pendant le mélange
        int[] tempValues = new int[size];
        for (int i = 0; i < size; i++) {
            tempValues[i] = map[startIdx + i];
        }

        long step = (2L * s + 1); // Précaution 'long' pour éviter overflow intermédiaire

        for (int i = 0; i < size; i++) {
            // Formule du sujet : position destination
            int newPos = (int) ((r + step * i) % size);

            // On place la ligne d'indice i (local) à la nouvelle position newPos (local)
            map[startIdx + newPos] = tempValues[i];
        }
    }

    /**
     * Applique le chiffrement ou déchiffrement sur une image OpenCV.
     * * @param src Image source
     * @param r Clé r
     * @param s Clé s
     * @param unscrambleMode false = Chiffrer (Mélanger), true = Déchiffrer (Remettre en ordre)
     * @return Nouvelle Mat traitée
     */
    public static Mat processImage(Mat src, int r, int s, boolean unscrambleMode) {
        int height = src.height();
        Mat dst = new Mat(src.size(), src.type());

        // map[i] indique où se trouve la ligne i dans l'image mélangée
        int[] map = getPermutationMap(height, r, s);

        for (int i = 0; i < height; i++) {
            if (unscrambleMode) {
                // MODE DÉCHIFFREMENT
                // On veut reconstruire la ligne i (image propre).
                // Elle est rangée à l'indice map[i] dans l'image mélangée (src).
                // Donc : Destination(i) <--- Source(map[i])
                Mat sourceRow = src.row(map[i]);
                Mat destRow = dst.row(i);
                sourceRow.copyTo(destRow);
            } else {
                // MODE CHIFFREMENT
                // La ligne i de l'image propre (src) doit aller à la position map[i].
                // Donc : Destination(map[i]) <--- Source(i)
                Mat sourceRow = src.row(i);
                Mat destRow = dst.row(map[i]);
                sourceRow.copyTo(destRow);
            }
        }
        return dst;
    }

    /**
     * ÉTAPE 2 : Casser la clé (Brute Force).
     * Trouve (r,s) qui maximise la cohérence verticale (Pearson).
     * Optimisé pour la performance (< 1s).
     * * @param scrambledImage Image chiffrée
     * @return Tableau {r, s} optimal
     */
    public static int[] crackKey(Mat scrambledImage) {
        // 1. Optimisation : Réduction de l'image pour analyse
        // On ne garde que ANALYSIS_WIDTH pixels de large et on passe en Niveaux de Gris.
        // Cela réduit drastiquement les calculs sans perdre l'info de corrélation.
        Mat analysisMat = new Mat();
        Imgproc.resize(scrambledImage, analysisMat, new Size(ANALYSIS_WIDTH, scrambledImage.height()));

        if (analysisMat.channels() > 1) {
            Imgproc.cvtColor(analysisMat, analysisMat, Imgproc.COLOR_BGR2GRAY);
        }

        // Conversion en double pour précision statistique
        analysisMat.convertTo(analysisMat, CvType.CV_64F);

        int h = analysisMat.rows();
        int w = analysisMat.cols();

        // 2. PRÉ-CALCULS STATISTIQUES (Indépendant de la clé)
        // Pour calculer Pearson rapidement entre deux lignes, on pré-calcule :
        // - Les données centrées (x - moyenne)
        // - L'inverse de la norme (1 / sqrt(variance))
        // Ainsi, dans la boucle, Pearson ne coûte qu'un produit scalaire.

        double[][] rowData = new double[h][w];
        double[] rowInvNorms = new double[h];

        for (int i = 0; i < h; i++) {
            analysisMat.row(i).get(0, 0, rowData[i]); // Récupère pixels bruts

            // Calcul Moyenne
            double sum = 0;
            for (double v : rowData[i]) sum += v;
            double mean = sum / w;

            // Centrage et Variance
            double sumSqDiff = 0;
            for (int j = 0; j < w; j++) {
                rowData[i][j] -= mean; // Centrage des données
                sumSqDiff += rowData[i][j] * rowData[i][j];
            }

            // Si variance est nulle (ligne unie), invNorm = 0
            rowInvNorms[i] = (sumSqDiff > 1e-9) ? 1.0 / Math.sqrt(sumSqDiff) : 0.0;
        }

        // 3. FORCE BRUTE PARALLÈLE
        // On teste les 32768 clés. Parallel Stream utilise tous les coeurs CPU.

        // Structure pour tenir le meilleur score thread-safe
        class Result {
            double score = -Double.MAX_VALUE;
            int r = 0, s = 0;
        }

        Result bestResult = IntStream.range(0, S_MAX).parallel().mapToObj(s -> {
            Result localBest = new Result();

            for (int r = 0; r < R_MAX; r++) {
                // Pour une clé (r,s), on obtient la map qui dit où sont les lignes
                int[] map = getPermutationMap(h, r, s);

                // Calcul du Score Global de l'image hypothétiquement déchiffrée
                // On additionne les corrélations entre la ligne reconstruite i et i+1.
                // La ligne reconstruite i correspond à la ligne map[i] de l'image brouillée.
                double totalCorrelation = 0;

                for (int i = 0; i < h - 1; i++) {
                    int rowIdxA = map[i];     // Index réel dans l'image brouillée
                    int rowIdxB = map[i + 1]; // Index réel du voisin supposé

                    // Produit scalaire des vecteurs centrés
                    double dotProduct = 0;
                    double[] vecA = rowData[rowIdxA];
                    double[] vecB = rowData[rowIdxB];

                    // Boucle manuelle pour perf max
                    for (int k = 0; k < w; k++) {
                        dotProduct += vecA[k] * vecB[k];
                    }

                    // Pearson = dotProduct / (normA * normB)
                    // On a précalculé invNorm = 1/norm
                    totalCorrelation += dotProduct * rowInvNorms[rowIdxA] * rowInvNorms[rowIdxB];
                }

                if (totalCorrelation > localBest.score) {
                    localBest.score = totalCorrelation;
                    localBest.r = r;
                    localBest.s = s;
                }
            }
            return localBest;
        }).reduce(new Result(), (a, b) -> (a.score > b.score) ? a : b);

        System.out.println("Clé trouvée : R=" + bestResult.r + ", S=" + bestResult.s +
                " (Score Pearson cumulé: " + String.format("%.2f", bestResult.score) + ")");

        return new int[]{bestResult.r, bestResult.s};
    }
}