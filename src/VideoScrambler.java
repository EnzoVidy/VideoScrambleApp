import org.opencv.core.Mat;

public class VideoScrambler {
    private int[] forwardMap;
    private int[] inverseMap;

    private int lastHeight = -1;
    private int lastR = -1;
    private int lastS = -1;

    public VideoScrambler() {
    }

    public void ensureMapComputed(int h, int r, int s) {
        if (h == lastHeight && r == lastR && s == lastS) {
            return;
        }

        this.forwardMap = new int[h];
        this.inverseMap = new int[h];

        int remainingLines = h;
        int offset = 0;

        while (remainingLines > 0) {
            int size = Integer.highestOneBit(remainingLines);

            for (int i = 0; i < size; i++) {
                long term = (long)r + (2L * s + 1L) * i;
                int newPosRelative = (int)(term % size);

                int sourceLine = i + offset;
                int destLine = newPosRelative + offset;

                forwardMap[sourceLine] = destLine;
                inverseMap[destLine] = sourceLine;
            }

            offset += size;
            remainingLines -= size;
        }
        this.lastHeight = h;
        this.lastR = r;
        this.lastS = s;

        System.out.println("Map recalculée pour H=" + h + ", R=" + r + ", S=" + s);
    }

    public Mat scramble(Mat input) {
        Mat output = new Mat(input.rows(), input.cols(), input.type());
        for (int i = 0; i < input.rows(); i++) {
            Mat sourceRow = input.row(i);
            Mat destRow = output.row(forwardMap[i]);
            sourceRow.copyTo(destRow);
        }
        return output;
    }

    public Mat unscramble(Mat input) {
        Mat output = new Mat(input.rows(), input.cols(), input.type());
        for (int i = 0; i < input.rows(); i++) {
            Mat sourceRow = input.row(forwardMap[i]);
            Mat destRow = output.row(i);
            sourceRow.copyTo(destRow);
        }
        return output;
    }

    /* * TODO POUR ÉTAPE 2 : FORCE BRUTE
     * Implémenter ici une méthode qui boucle sur r (0-255) et s (0-127),
     * déchiffre, calcule la distance Euclidienne ou Pearson entre lignes adjacentes,
     * et retourne la meilleure paire (r,s).
     */
}