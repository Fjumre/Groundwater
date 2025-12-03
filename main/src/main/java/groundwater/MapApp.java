package groundwater;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;



public class MapApp extends Application {

    public SplitGroundwaterCSV splitGroundwaterCSV = new SplitGroundwaterCSV();
    private WebEngine engine;
private final CRSFactory crsFactory = new CRSFactory();
private final CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
private final CoordinateReferenceSystem srcCRS =
        crsFactory.createFromName("EPSG:25832");  // your X/Y system
private final CoordinateReferenceSystem dstCRS =
        crsFactory.createFromName("EPSG:4326");   // WGS84 lat/lon
private final CoordinateTransform transform =
        ctFactory.createTransform(srcCRS, dstCRS);

    public static void main(String[] args) {
        System.setProperty("glass.win.uiScale", "100%");
        System.setProperty("prism.allowhidpi", "false");

        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
    
        launch(args);
        System.out.println("Loading groundwater data...");

    }

   @Override
public void start(Stage stage) {

    WebView webView = new WebView();
    engine = webView.getEngine();

    webView.setZoom(1.0);
    webView.setStyle("""
        -fx-snap-to-pixel: true;
        -fx-font-smoothing-type: lcd;
    """);

    webView.layoutBoundsProperty().addListener((obs, oldB, newB) -> {
        double w = Math.floor(newB.getWidth());
        double h = Math.floor(newB.getHeight());
        webView.setPrefSize(w, h);
    });

    engine.load(getClass().getResource("/map.html").toExternalForm());
/*engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
    if (newState == Worker.State.SUCCEEDED) {
        System.out.println("Map HTML fully loaded.");

        // NEW: Generate tiles if they don't exist
        try {
            SplitGroundwaterCSV.splitGroundwaterCSV(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Now load tiles
        new Thread(this::loadAllTiles).start();
    }
});
   engine.documentProperty().addListener((obs, oldDoc, newDoc) -> {
        if (newDoc != null) {
            new Thread(() -> loadAllTiles()).start();
        }
    });*/

 engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
        if (newState == Worker.State.SUCCEEDED) {
            System.out.println("Map HTML fully loaded, starting tile loading...");
            new Thread(this::loadAllTiles).start();
        }
    });
    stage.setScene(new Scene(webView, 1200, 800));


    // Fix #2: Force scene to pixel-aligned rendering
    stage.getScene().getRoot().setStyle("-fx-snap-to-pixel:true;");

    // Fix #3: Prevent fractional stage dimensions
    stage.widthProperty().addListener((obs, oldV, newV) ->
        stage.setWidth(Math.floor(newV.doubleValue()))
    );
    stage.heightProperty().addListener((obs, oldV, newV) ->
        stage.setHeight(Math.floor(newV.doubleValue()))
    );

    stage.setTitle("Groundwater Levels Viewer");
    stage.show();
}

private String cleanWkt(String wkt) {

    if (wkt == null) return null;

    // remove quotes
    wkt = wkt.replace("\"", "");

    // remove tabs & weird whitespace
    wkt = wkt.replaceAll("[\\t\\r\\n]", " ");

    // collapse multiple spaces
    wkt = wkt.replaceAll(" +", " ");

    // ensure MULTILINESTRING outer parentheses are balanced
    wkt = wkt.trim();

    // sometimes ends with stray comma
    while (wkt.endsWith(",")) {
        wkt = wkt.substring(0, wkt.length() - 1).trim();
    }

    // remove trailing semicolon if CSV had one
    if (wkt.endsWith(";")) {
        wkt = wkt.substring(0, wkt.length() - 1).trim();
    }

    // some rows end with ))" – bad
    if (wkt.endsWith(")\"")) {
        wkt = wkt.substring(0, wkt.length() - 2).trim();
    }

    return wkt;
}

    /** Load CSV and send lines to the map */
   private void loadAllTiles() {
    File tileDir = new File("data/tiles/");
    System.out.println("Looking for tiles in: " + tileDir.getAbsolutePath());

    File[] tiles = tileDir.listFiles((d, name) -> name.endsWith(".csv"));

    if (tiles == null || tiles.length == 0) {
        System.out.println("No tile CSVs found in " + tileDir.getAbsolutePath());
        return;
    }

    // Sort files alphabetically
    Arrays.sort(tiles);

    new Thread(() -> {
        for (File f : tiles) {
            System.out.println("Loading: " + f.getName());
            loadGroundwaterData(f.getAbsolutePath());

            // avoid freezing
            try { Thread.sleep(150); } catch (Exception ignore) {}
        }
    }).start();
}

   private void loadGroundwaterData(String filename) {
    try (BufferedReader br = new BufferedReader(new FileReader(filename))) {

        String line;
        boolean header = true;

        GeometryFactory gf = new GeometryFactory();
        WKTReader reader = new WKTReader(gf);

        // Batch buffer
        StringBuilder batch = new StringBuilder();

        int jsCount = 0;

        while ((line = br.readLine()) != null) {

            if (header) {
                header = false;
                continue;
            }

            String[] cols = line.split(";", -1);

            // Find WKT start
            int wktStart = -1;
            for (int i = 0; i < cols.length; i++) {
                if (cols[i].contains("MULTILINESTRING")) {
                    wktStart = i;
                    break;
                }
            }
            if (wktStart == -1) continue;

            // Build full WKT
            StringBuilder wktBuilder = new StringBuilder();
            for (int i = wktStart; i < cols.length; i++) {
                wktBuilder.append(cols[i]);
            }
            String rawWkt = cleanWkt(wktBuilder.toString());

            // Parse kote
            int kote = 0;
            try { kote = Integer.parseInt(cols[4]); } catch (Exception ignore) {}

            // Parse geometry
            Geometry geom;
            try {
                geom = reader.read(rawWkt);
            } catch (Exception e) {
                System.err.println("Invalid WKT skipped: " + rawWkt);
                continue;
            }

            // Convert geometry parts
            int numGeom = geom.getNumGeometries();

            for (int gIndex = 0; gIndex < numGeom; gIndex++) {

                Geometry part = geom.getGeometryN(gIndex);
                if (!(part instanceof LineString)) continue;

                LineString ls = (LineString) part;
                Coordinate[] coords = ls.getCoordinates();

                // Build JS in memory
                batch.append("addLine([");
                for (int i = 0; i < coords.length; i++) {
                    Coordinate c = coords[i];
                    double[] latlon = toLatLon(c.x, c.y);

                    batch.append("[")
                            .append(latlon[0]).append(",")
                            .append(latlon[1]).append("]");

                    if (i < coords.length - 1) batch.append(",");
                }
                batch.append("], ").append(kote).append(");\n");

                jsCount++;

                // flush every 500 lines
                if (jsCount >= 500) {
                    String jsChunk = batch.toString();
                    Platform.runLater(() -> engine.executeScript(jsChunk));
                    batch.setLength(0);
                    jsCount = 0;
                }
            }
        }

        // flush remaining lines
        if (batch.length() > 0) {
            String jsChunk = batch.toString();
            Platform.runLater(() -> engine.executeScript(jsChunk));
        }

        System.out.println("Finished loading tile: " + filename);

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



private double[] toLatLon(double x, double y) {
    ProjCoordinate in = new ProjCoordinate(x, y);
    ProjCoordinate out = new ProjCoordinate();
    transform.transform(in, out);
    return new double[]{out.y, out.x};  // Leaflet uses [lat, lon]
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
