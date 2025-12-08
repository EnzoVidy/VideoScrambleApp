import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

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

    private ScheduledExecutorService timer;
    private VideoCapture capture = new VideoCapture();
    private boolean cameraActive = false;
    private VideoWriter writer;
    private boolean isRecording = false;

    // Toggle Group pour les modes
    private ToggleGroup modeGroup;

    public void initialize() {
        modeGroup = new ToggleGroup();
        btnScramble.setToggleGroup(modeGroup);
        btnUnscramble.setToggleGroup(modeGroup);
    }

    @FXML
    protected void startCamera(ActionEvent event) {
        if (!this.cameraActive) {
            this.capture.open(0); // Webcam 0
            startAcquisition("output_cam_processed.avi");
            this.btnCamera.setText("Stop Camera");
        } else {
            this.cameraActive = false;
            this.btnCamera.setText("Start Camera");
            this.stopAcquisition();
        }
    }

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
            // Nom de sortie basé sur l'entrée
            startAcquisition(file.getName().replace(".", "_processed."));
        }
    }

    private void startAcquisition(String outputFilename) {
        if (this.capture.isOpened()) {
            this.cameraActive = true;

            // Setup VideoWriter (codec MJPG pour simplicité, ou FFV1 si dispo)
            // Attention: VideoWriter peut échouer selon les codecs installés sur l'OS.
            int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G');
            double fps = capture.get(Videoio.CAP_PROP_FPS);
            if(fps <= 0) fps = 30.0;
            Size frameSize = new Size(
                    (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH),
                    (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT)
            );

            this.writer = new VideoWriter(outputFilename, fourcc, fps, frameSize, true);
            this.isRecording = writer.isOpened();
            if(!isRecording) System.err.println("Warning: Could not create video writer.");

            Runnable frameGrabber = () -> {
                Mat frame = grabFrame();
                if (!frame.empty()) {
                    // 1. Afficher l'original
                    Image imgOrig = mat2Image(frame);
                    updateImageView(originalFrame, imgOrig);

                    // 2. Traitement
                    Mat processed = processFrame(frame);

                    // 3. Enregistrement
                    if (isRecording && processed != null) {
                        writer.write(processed);
                    }

                    // 4. Afficher le traité
                    Image imgProc = mat2Image(processed);
                    updateImageView(processedFrame, imgProc);
                } else {
                    // Fin de vidéo ou erreur
                    System.out.println("End of stream");
                    Platform.runLater(() -> stopAcquisition());
                }
            };

            this.timer = Executors.newSingleThreadScheduledExecutor();
            this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);
        }
    }

    private Mat processFrame(Mat input) {
        try {
            int r = Integer.parseInt(txtR.getText());
            int s = Integer.parseInt(txtS.getText());

            if (btnScramble.isSelected()) {
                // Mode Chiffrement
                return VideoScrambler.processImage(input, r, s, false);
            } else if (btnUnscramble.isSelected()) {
                // Mode Déchiffrement
                return VideoScrambler.processImage(input, r, s, true);
            }
        } catch (NumberFormatException e) {}
        // Si aucun mode sélectionné, on renvoie une copie de l'original
        return input.clone();
    }

    @FXML
    protected void crackKey(ActionEvent event) {
        // Pour casser la clé, on capture une seule frame actuelle de la source
        // ATTENTION: La source doit être une image chiffrée pour que cela ait du sens.
        // Si vous chargez une vidéo chiffrée, 'originalFrame' contient l'image mélangée.

        if (this.capture.isOpened()) {
            Mat frame = new Mat();
            this.capture.read(frame); // Lecture d'une frame à la volée

            if (!frame.empty()) {
                System.out.println("Starting Brute Force on current frame...");
                // On lance ça dans un thread séparé pour ne pas geler l'UI
                new Thread(() -> {
                    long start = System.currentTimeMillis();
                    int[] key = VideoScrambler.crackKey(frame);
                    long end = System.currentTimeMillis();

                    Platform.runLater(() -> {
                        txtR.setText(String.valueOf(key[0]));
                        txtS.setText(String.valueOf(key[1]));
                        // On passe automatiquement en mode Déchiffrement pour vérifier
                        btnUnscramble.setSelected(true);
                        System.out.println("Crack finished in " + (end-start) + "ms");
                    });
                }).start();
            }
        }
    }

    private Mat grabFrame() {
        Mat frame = new Mat();
        if (this.capture.isOpened()) {
            try {
                this.capture.read(frame);
            } catch (Exception e) {
                System.err.println("Exception during capture: " + e);
            }
        }
        return frame;
    }

    private void stopAcquisition() {
        if (this.timer != null && !this.timer.isShutdown()) {
            try {
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                System.err.println("Exception in stopping timer: " + e);
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
        if(original.empty()) return null;
        BufferedImage image = null;
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
