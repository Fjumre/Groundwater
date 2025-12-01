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

    private WebEngine engine;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {

        WebView webView = new WebView();
        engine = webView.getEngine();

        // Load Leaflet map
        engine.load(getClass().getResource("/map.html").toExternalForm());

        // When the page is ready, load data
        engine.documentProperty().addListener((obs, oldDoc, newDoc) -> {
            if (newDoc != null) {
                new Thread(() -> loadGroundwaterData("WaterLevel.csv")).start();
            }
        });

        stage.setScene(new Scene(webView, 1200, 800));
        stage.setTitle("Groundwater Levels Viewer");
        stage.show();
    }

    /** Load CSV and send lines to the map */
    private void loadGroundwaterData(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {

            String line;
            boolean header = true;

            while ((line = br.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }

                String[] cols = line.split(",");

                int kote = Integer.parseInt(cols[4]);       // groundwater level
                String wkt = cols[cols.length - 1];         // shape_wkt column

                List<List<double[]>> lines = parseMultiLineString(wkt);

                List<List<double[]>> latLonLines = new ArrayList<>();

                for (List<double[]> seg : lines) {
                    List<double[]> converted = new ArrayList<>();
                    for (double[] xy : seg) {
                        converted.add(toLatLon(xy[0], xy[1]));
                    }
                    latLonLines.add(converted);
                }

                String js = toJson(latLonLines);

                String call = String.format("addLine(%s, %d);", js, kote);

                javafx.application.Platform.runLater(() ->
                        engine.executeScript(call)
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Parse MULTILINESTRING ((x y, x y), (x y, x y)) */
    private List<List<double[]>> parseMultiLineString(String wkt) {
        List<List<double[]>> result = new ArrayList<>();

        String inner = wkt
                .replace("MULTILINESTRING", "")
                .replace("((", "")
                .replace("))", "");

        String[] segments = inner.split("\\), \\(");

        for (String seg : segments) {
            List<double[]> coords = new ArrayList<>();
            String[] pairs = seg.split(",");

            for (String p : pairs) {
                p = p.trim();
                if (p.isEmpty()) continue;

                String[] xy = p.split(" ");
                double x = Double.parseDouble(xy[0]);
                double y = Double.parseDouble(xy[1]);

                coords.add(new double[]{x, y});
            }
            result.add(coords);
        }
        return result;
    }

    /** Convert UTM32 (EPSG:25832) to WGS84 */
    private double[] toLatLon(double x, double y) {
        double lon = (x / 20037508.34) * 180;
        double lat = (y / 20037508.34) * 180;
        lat = 180 / Math.PI * (2 * Math.atan(Math.exp(lat * Math.PI / 180)) - Math.PI / 2);
        return new double[]{lat, lon};
    }

    /** Convert to JSON manually */
    private String toJson(List<List<double[]>> lines) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lines.size(); i++) {
            sb.append("[");
            List<double[]> seg = lines.get(i);
            for (int j = 0; j < seg.size(); j++) {
                double[] c = seg.get(j);
                sb.append("[").append(c[0]).append(",").append(c[1]).append("]");
                if (j < seg.size() - 1) sb.append(",");
            }
            sb.append("]");
            if (i < lines.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
