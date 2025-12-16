/*
 * Noms    : Boisselot, Vidy
 * Prénoms : Harry, Enzo
 * Groupe  : S5-A2
 */

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.stream.IntStream;

/**
 * Classe utilitaire gérant la logique algorithmique du projet.
 * Inclut : Mélange de lignes, Cryptanalyse (Brute Force) et Stéganographie.
 */
public class VideoScrambler {

    // Largeur réduite pour l'analyse statistique rapide (Force Brute)
    private static final int ANALYSIS_WIDTH = 64;

    private static final int R_MAX = 256;
    private static final int S_MAX = 128;

    /**
     * Génère la carte de permutation des lignes selon la clé (r, s).
     * Utilise une approche récursive par blocs de puissance de 2.
     *
     * @param height Hauteur de l'image.
     * @param r Composante R de la clé (décalage).
     * @param s Composante S de la clé (pas).
     * @return Un tableau où tab[i] est la nouvelle position de la ligne i.
     */
    public static int[] getPermutationMap(int height, int r, int s) {
        int[] map = new int[height];
        for (int i = 0; i < height; i++) map[i] = i;

        int currentStart = 0;
        int remainingLines = height;

        // Traitement itératif des blocs (puissances de 2)
        while (remainingLines > 0) {
            int blockSize = (remainingLines >= 2) ? Integer.highestOneBit(remainingLines) : remainingLines;

            // Application du mélange sur ce bloc
            permuteBlock(map, currentStart, blockSize, r, s);

            currentStart += blockSize;
            remainingLines -= blockSize;
        }
        return map;
    }

    /**
     * Applique la formule de permutation sur un sous-bloc spécifique du tableau.
     * Formule : pos = (r + (2s+1) * id) % size.
     *
     * @param map Tableau de mapping global.
     * @param startIdx Indice de début du bloc à traiter.
     * @param size Taille du bloc (puissance de 2).
     * @param r Clé R.
     * @param s Clé S.
     */
    private static void permuteBlock(int[] map, int startIdx, int size, int r, int s) {
        if (size <= 1) return;

        int[] tempValues = new int[size];
        for (int i = 0; i < size; i++) {
            tempValues[i] = map[startIdx + i];
        }

        long step = (2L * s + 1);

        for (int i = 0; i < size; i++) {
            int newPos = (int) ((r + step * i) % size);
            map[startIdx + newPos] = tempValues[i];
        }
    }

    /**
     * Traite une image OpenCV pour la chiffrer ou la déchiffrer.
     *
     * @param src Image source.
     * @param r Clé R.
     * @param s Clé S.
     * @param unscrambleMode Si true, effectue l'opération inverse (déchiffrement).
     * @return Une nouvelle Mat contenant l'image traitée.
     */
    public static Mat processImage(Mat src, int r, int s, boolean unscrambleMode) {
        int height = src.height();
        Mat dst = new Mat(src.size(), src.type());

        int[] map = getPermutationMap(height, r, s);

        for (int i = 0; i < height; i++) {
            if (unscrambleMode) {
                // Déchiffrement : Destination[i] reçoit Source[map[i]]
                Mat sourceRow = src.row(map[i]);
                Mat destRow = dst.row(i);
                sourceRow.copyTo(destRow);
            } else {
                // Chiffrement : Destination[map[i]] reçoit Source[i]
                Mat sourceRow = src.row(i);
                Mat destRow = dst.row(map[i]);
                sourceRow.copyTo(destRow);
            }
        }
        return dst;
    }

    /**
     * Recherche la clé (r, s) par force brute en utilisant la corrélation de Pearson.
     * Optimisé via redimensionnement et multi-threading.
     *
     * @param scrambledImage Image chiffrée à analyser.
     * @return Un tableau contenant {meilleur_R, meilleur_S}.
     */
    public static int[] crackKey(Mat scrambledImage) {
        // 1. Optimisation : Travail sur image réduite en niveaux de gris
        Mat analysisMat = new Mat();
        Imgproc.resize(scrambledImage, analysisMat, new Size(ANALYSIS_WIDTH, scrambledImage.height()));

        if (analysisMat.channels() > 1) {
            Imgproc.cvtColor(analysisMat, analysisMat, Imgproc.COLOR_BGR2GRAY);
        }
        analysisMat.convertTo(analysisMat, CvType.CV_64F);

        int h = analysisMat.rows();
        int w = analysisMat.cols();

        // 2. Pré-calculs statistiques (centrage et norme inverse) pour accélérer Pearson
        double[][] rowData = new double[h][w];
        double[] rowInvNorms = new double[h];

        for (int i = 0; i < h; i++) {
            analysisMat.row(i).get(0, 0, rowData[i]);

            double sum = 0;
            for (double v : rowData[i]) sum += v;
            double mean = sum / w;

            double sumSqDiff = 0;
            for (int j = 0; j < w; j++) {
                rowData[i][j] -= mean;
                sumSqDiff += rowData[i][j] * rowData[i][j];
            }
            rowInvNorms[i] = (sumSqDiff > 1e-9) ? 1.0 / Math.sqrt(sumSqDiff) : 0.0;
        }

        // 3. Recherche parallèle
        class Result {
            double score = -Double.MAX_VALUE;
            int r = 0, s = 0;
        }

        Result bestResult = IntStream.range(0, S_MAX).parallel().mapToObj(s -> {
            Result localBest = new Result();

            for (int r = 0; r < R_MAX; r++) {
                int[] map = getPermutationMap(h, r, s);
                double totalCorrelation = 0;

                // Calcul de la cohérence entre lignes adjacentes reconstruites
                for (int i = 0; i < h - 1; i++) {
                    int rowIdxA = map[i];
                    int rowIdxB = map[i + 1];

                    double dotProduct = 0;
                    double[] vecA = rowData[rowIdxA];
                    double[] vecB = rowData[rowIdxB];

                    for (int k = 0; k < w; k++) {
                        dotProduct += vecA[k] * vecB[k];
                    }
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
                " (Score: " + String.format("%.2f", bestResult.score) + ")");

        return new int[]{bestResult.r, bestResult.s};
    }

    /**
     * Cache la clé dans le pixel (0,0) de l'image (Stéganographie).
     * Utilise les 5 bits de poids faible de chaque canal (R, G, B).
     *
     * @param src Image source.
     * @param r Clé R à cacher.
     * @param s Clé S à cacher.
     * @return Nouvelle Mat avec la clé embarquée.
     */
    public static Mat embedKey(Mat src, int r, int s) {
        Mat dst = src.clone();
        double[] pixel = dst.get(0, 0);
        if (pixel == null) return dst;

        // Découpage des bits : R (8 bits) + S (7 bits) = 15 bits
        // B (5 bits) : S[0-4]
        // G (5 bits) : R[0-2] + S[5-6]
        // R (5 bits) : R[3-7]

        int bitsForRed = (r >> 3) & 0b11111;
        int bitsForGreen = ((r & 0b111) << 2) | ((s >> 5) & 0b11);
        int bitsForBlue = s & 0b11111;

        pixel[0] = ((int)pixel[0] & 0xE0) | bitsForBlue;
        pixel[1] = ((int)pixel[1] & 0xE0) | bitsForGreen;
        pixel[2] = ((int)pixel[2] & 0xE0) | bitsForRed;

        dst.put(0, 0, pixel);
        return dst;
    }

    /**
     * Extrait la clé cachée dans le pixel (0,0).
     *
     * @param src Image contenant potentiellement une clé.
     * @return Un tableau {r, s} extrait.
     */
    public static int[] extractKey(Mat src) {
        double[] pixel = src.get(0, 0);
        if (pixel == null) return new int[]{0, 0};

        int valBlue  = (int)pixel[0] & 0xFF;
        int valGreen = (int)pixel[1] & 0xFF;
        int valRed   = (int)pixel[2] & 0xFF;

        int bLsb = valBlue & 0b11111;
        int gLsb = valGreen & 0b11111;
        int rLsb = valRed & 0b11111;

        int s = ((gLsb & 0b11) << 5) | bLsb;
        int r = (rLsb << 3) | (gLsb >> 2);

        return new int[]{r, s};
    }
}