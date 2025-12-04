import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

public class VideoScramblerController {

    @FXML private Button button;
    @FXML private ImageView originalFrame;
    @FXML private ImageView processedFrame;
    @FXML private TextField rField;
    @FXML private TextField sField;
    @FXML private CheckBox chkDescramble;

    private ScheduledExecutorService timer;
    private VideoCapture capture = new VideoCapture();
    private boolean cameraActive = false;
    private static int cameraId = 0; // 0 = webcam par défaut

    private VideoScrambler scrambler = new VideoScrambler();

    @FXML
    protected void startCamera(ActionEvent event) {
        if (!this.cameraActive) {
            this.capture.open(cameraId);

            if (this.capture.isOpened()) {
                this.cameraActive = true;

                // Grab a frame every 33 ms (30 fps)
                Runnable frameGrabber = () -> {
                    Mat frame = grabFrame();
                    if (!frame.empty()) {

                        Image imageOriginal = mat2Image(frame);
                        updateImageView(originalFrame, imageOriginal);

                        int r = 0;
                        int s = 0;
                        try {
                            r = Integer.parseInt(rField.getText()) % 256;
                            s = Integer.parseInt(sField.getText()) % 128;
                        } catch (NumberFormatException e) {
                            r = 1;
                            s = 128;
                        }

                        scrambler.ensureMapComputed(frame.rows(), r, s);

                        Mat processedMat;
                        if (chkDescramble.isSelected()) {

                            Mat tempScrambled = scrambler.scramble(frame);
                            processedMat = scrambler.unscramble(tempScrambled);

                            tempScrambled.release();
                        } else {
                            processedMat = scrambler.scramble(frame);
                        }

                        Image imageProcessed = mat2Image(processedMat);
                        updateImageView(processedFrame, imageProcessed);

                        processedMat.release();
                        frame.release();
                    }
                };

                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

                this.button.setText("Arrêter Caméra");
            } else {
                System.err.println("Impossible d'ouvrir la caméra...");
            }
        } else {
            this.cameraActive = false;
            this.button.setText("Démarrer Caméra");
            this.stopAcquisition();
        }
    }

    private Mat grabFrame() {
        Mat frame = new Mat();
        if (this.capture.isOpened()) {
            try {
                this.capture.read(frame);
            } catch (Exception e) {
                System.err.println("Exception lors de la capture: " + e);
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
                System.err.println("Exception à l'arrêt: " + e);
            }
        }
        if (this.capture.isOpened()) {
            this.capture.release();
        }
    }

    private void updateImageView(ImageView view, Image image) {
        onFXThread(view.imageProperty(), image);
    }

    protected void setClosed() {
        this.stopAcquisition();
    }

    public static Image mat2Image(Mat frame) {
        try {
            return SwingFXUtils.toFXImage(matToBufferedImage(frame), null);
        } catch (Exception e) {
            System.err.println("Cannot convert the Mat object: " + e);
            return null;
        }
    }

    private static BufferedImage matToBufferedImage(Mat original) {
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

    public static <T> void onFXThread(final ObjectProperty<T> property, final T value) {
        Platform.runLater(() -> {
            property.set(value);
        });
    }
}