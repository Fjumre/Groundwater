package groundwater;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

public class MapApp extends Application {

    public static class Well {
        String name;
        double lat;
        double lon;
        double waterLevel;

        public Well(String name, double lat, double lon, double waterLevel) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.waterLevel = waterLevel;
        }
    }

    private List<Well> loadCSV(String file) {
        List<Well> wells = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",");
                wells.add(new Well(p[0],
                                   Double.parseDouble(p[1]),
                                   Double.parseDouble(p[2]),
                                   Double.parseDouble(p[3])));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return wells;
    }

    @Override
    public void start(Stage stage) {
        WebView view = new WebView();
        WebEngine engine = view.getEngine();

        engine.load(getClass().getResource("/map.html").toExternalForm());

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
    if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
            List<Well> wells = loadCSV("WaterAbstraction.csv");

            for (Well well : wells) {
                String js = String.format(
                    "addMarker(%f, %f, '%s<br>Water level: %s m');",
                    well.lat, well.lon, well.name, well.waterLevel
                );
                engine.executeScript(js);
            }
    }
        });

        stage.setScene(new Scene(view, 900, 600));
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
