import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.opencv.core.Core;

public class VideoScramblerApp extends Application {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("VideoScramblerView.fxml"));
            BorderPane rootElement = (BorderPane) loader.load();

            Scene scene = new Scene(rootElement, 1100, 700);

            primaryStage.setTitle("Projet VideoScramble - OpenCV & JavaFX");
            primaryStage.setScene(scene);
            primaryStage.show();

            VideoScramblerController controller = loader.getController();
            primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
                public void handle(WindowEvent we) {
                    controller.setClosed();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}