/*
 * Noms    : Boisselot, Vidy
 * Prénoms : Harry, Enzo
 * Groupe  : S5-A2
 */
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.opencv.core.Core;

/**
 * Point d'entrée de l'application VideoScrambler.
 * Charge l'interface FXML et initialise la bibliothèque OpenCV.
 */
public class VideoScramblerApp extends Application {

    // Chargement de la bibliothèque native OpenCV
    static { System.loadLibrary(Core.NATIVE_LIBRARY_NAME); }

    /**
     * Méthode principale de démarrage de l'application JavaFX.
     *
     * @param primaryStage La fenêtre principale de l'application.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("VideoScramblerView.fxml"));
            BorderPane rootElement = (BorderPane) loader.load();

            // Dimensions ajustées pour afficher deux flux vidéo côte à côte
            Scene scene = new Scene(rootElement, 1100, 700);

            primaryStage.setTitle("Projet VideoScramble");
            primaryStage.setScene(scene);
            primaryStage.show();

            // Gestion de la fermeture propre de l'application
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

    /**
     * Point d'entrée standard pour les applications Java.
     *
     * @param args Arguments de la ligne de commande.
     */
    public static void main(String[] args) {
        launch(args);
    }
}