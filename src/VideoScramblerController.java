/*
 * Noms    : Boisselot, Vidy
 * Prénoms : Harry, Enzo
 * Groupe  : S5-A2
 */

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

/**
 * Contrôleur de l'application.
 * Gère les interactions UI, la capture vidéo, l'enregistrement et les appels au traitement d'image.
 */
public class VideoScramblerController {

    @FXML private Button btnCamera;
    @FXML private Button btnFile;
    @FXML private Button btnCrack;
    @FXML private ImageView originalFrame;
    @FXML private ImageView processedFrame;
    @FXML private TextField txtR;
    @FXML private TextField txtS;
    @FXML private ToggleButton btnScramble;
    @FXML private ToggleButton btnUnscramble;
    @FXML private CheckBox chkStego;

    private ScheduledExecutorService timer;
    private VideoCapture capture = new VideoCapture();
    private boolean cameraActive = false;
    private VideoWriter writer;
    private boolean isRecording = false;

    // Variable tampon pour stocker la dernière image vue (thread-safe pour le crack)
    private Mat lastFrame = new Mat();

    private ToggleGroup modeGroup;

    /**
     * Initialisation du contrôleur et des groupes de boutons.
     */
    public void initialize() {
        modeGroup = new ToggleGroup();
        btnScramble.setToggleGroup(modeGroup);
        btnUnscramble.setToggleGroup(modeGroup);
    }

    /**
     * Action déclenchée par le bouton "Start Camera".
     */
    @FXML
    protected void startCamera(ActionEvent event) {
        if (!this.cameraActive) {
            this.capture.open(0);
            startAcquisition("output_cam_processed.avi");
            this.btnCamera.setText("Stop Camera");
        } else {
            this.cameraActive = false;
            this.btnCamera.setText("Start Camera");
            this.stopAcquisition();
        }
    }

    /**
     * Action déclenchée par le bouton "Load Video File".
     */
    @FXML
    protected void loadFile(ActionEvent event) {
        if (this.cameraActive) {
            stopAcquisition();
            this.cameraActive = false;
            this.btnCamera.setText("Start Camera");
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Video File");
        File file = fileChooser.showOpenDialog(btnFile.getScene().getWindow());

        if (file != null) {
            this.capture.open(file.getAbsolutePath());
            startAcquisition(file.getName().replace(".", "_processed."));
        }
    }

    /**
     * Démarre le thread d'acquisition et l'enregistrement vidéo.
     */
    private void startAcquisition(String outputFilename) {
        if (this.capture.isOpened()) {
            this.cameraActive = true;

            // Utilisation de FFV1 pour éviter la compression destructrice
            int fourcc = VideoWriter.fourcc('F', 'F', 'V', '1');

            if (!outputFilename.endsWith(".avi") && !outputFilename.endsWith(".mkv")) {
                outputFilename += ".avi";
            }

            double fps = capture.get(Videoio.CAP_PROP_FPS);
            if (fps <= 0) fps = 30.0;
            Size frameSize = new Size(
                    (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH),
                    (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT)
            );

            this.writer = new VideoWriter(outputFilename, fourcc, fps, frameSize, true);
            this.isRecording = writer.isOpened();
            if (!isRecording) System.err.println("Warning: Could not create video writer.");

            Runnable frameGrabber = () -> {
                Mat frame = grabFrame();
                if (!frame.empty()) {
                    // On garde une copie de la frame actuelle pour que la fonction "Crack"
                    // puisse l'utiliser sans toucher au flux vidéo (éviter le conflit de thread).
                    frame.copyTo(lastFrame);

                    // 1. Affichage Source
                    Image imgOrig = mat2Image(frame);
                    updateImageView(originalFrame, imgOrig);

                    // 2. Traitement (Chiffrement/Déchiffrement)
                    Mat processed = processFrame(frame);

                    // 3. Enregistrement
                    if (isRecording && processed != null) {
                        writer.write(processed);
                    }

                    // 4. Affichage Sortie
                    Image imgProc = mat2Image(processed);
                    updateImageView(processedFrame, imgProc);
                } else {
                    System.out.println("End of stream");
                    Platform.runLater(this::stopAcquisition);
                }
            };

            this.timer = Executors.newSingleThreadScheduledExecutor();
            this.timer.scheduleAtFixedRate(frameGrabber, 0, 15, TimeUnit.MILLISECONDS);
        } else {
            System.err.println("Impossible to open the camera connection...");
        }
    }

    /**
     * Applique la logique de traitement sur une frame.
     */
    private Mat processFrame(Mat input) {
        try {
            int r = Integer.parseInt(txtR.getText());
            int s = Integer.parseInt(txtS.getText());

            if (btnScramble.isSelected()) {
                // Mode Chiffrement
                Mat processed = VideoScrambler.processImage(input, r, s, false);
                if (chkStego.isSelected()) {
                    processed = VideoScrambler.embedKey(processed, r, s);
                }
                return processed;

            } else if (btnUnscramble.isSelected()) {
                // Mode Déchiffrement
                if (chkStego.isSelected()) {
                    int[] key = VideoScrambler.extractKey(input);
                    r = key[0];
                    s = key[1];
                    int finalR = r;
                    int finalS = s;
                    Platform.runLater(() -> {
                        txtR.setText(String.valueOf(finalR));
                        txtS.setText(String.valueOf(finalS));
                    });
                }
                return VideoScrambler.processImage(input, r, s, true);
            }
        } catch (NumberFormatException e) {
            // Ignorer erreurs parsing
        }
        return input.clone();
    }

    /**
     * Lance la recherche de clé par force brute sur la frame courante.
     */
    @FXML
    protected void crackKey(ActionEvent event) {
        // Vérification que nous avons une image valide en mémoire
        if (!lastFrame.empty()) {

            // On clone l'image pour que le thread de calcul travaille sur sa propre copie
            // et ne soit pas perturbé par le timer qui met à jour lastFrame.
            Mat frameToCrack = lastFrame.clone();

            System.out.println("Starting Brute Force on current frame...");

            new Thread(() -> {
                long start = System.currentTimeMillis();

                // Appel de la méthode optimisée dans VideoScrambler
                int[] key = VideoScrambler.crackKey(frameToCrack);

                long end = System.currentTimeMillis();

                Platform.runLater(() -> {
                    txtR.setText(String.valueOf(key[0]));
                    txtS.setText(String.valueOf(key[1]));

                    btnUnscramble.setSelected(true);

                    System.out.println("Crack finished in " + (end - start) + "ms");

                    new Alert(Alert.AlertType.INFORMATION,
                            "Clé trouvée : R=" + key[0] + ", S=" + key[1] + "\nTemps: " + (end-start) + "ms").show();
                });
            }).start();
        } else {
            System.out.println("Aucune image à analyser (lancez la caméra ou une vidéo d'abord).");
        }
    }

    /**
     * Capture une frame depuis le flux vidéo ouvert.
     */
    private Mat grabFrame() {
        Mat frame = new Mat();
        if (this.capture.isOpened()) {
            try {
                this.capture.read(frame);
            } catch (Exception e) {
                System.err.println("Exception during the image elaboration: " + e);
            }
        }
        return frame;
    }

    /**
     * Arrête l'acquisition vidéo et libère les ressources.
     */
    private void stopAcquisition() {
        if (this.timer != null && !this.timer.isShutdown()) {
            try {
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                System.err.println("Exception in stopping the frame capture: " + e);
            }
        }
        if (this.capture.isOpened()) {
            this.capture.release();
        }
        if (this.writer != null && this.writer.isOpened()) {
            this.writer.release();
        }
        this.cameraActive = false;
    }

    public void setClosed() {
        this.stopAcquisition();
    }

    private void updateImageView(ImageView view, Image image) {
        onFXThread(view.imageProperty(), image);
    }

    public static <T> void onFXThread(final ObjectProperty<T> property, final T value) {
        Platform.runLater(() -> property.set(value));
    }

    public static Image mat2Image(Mat frame) {
        try {
            return SwingFXUtils.toFXImage(matToBufferedImage(frame), null);
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage matToBufferedImage(Mat original) {
        if (original.empty()) return null;
        BufferedImage image;
        int width = original.width(), height = original.height(), channels = original.channels();
        byte[] sourcePixels = new byte[width * height * channels];
        original.get(0, 0, sourcePixels);

        if (original.channels() > 1) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        } else {
            image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        }
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.length);
        return image;
    }
}