package groundwater;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
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
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;


public class MapApp extends Application {

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

    engine.documentProperty().addListener((obs, oldDoc, newDoc) -> {
        if (newDoc != null) {
            new Thread(() -> loadGroundwaterData("GroundWater.csv")).start();
        }
    });

    stage.setScene(new Scene(webView, 1200, 800));

    // ------------- REQUIRED FIXES ------------------

    // Fix #2: Force scene to pixel-aligned rendering
    stage.getScene().getRoot().setStyle("-fx-snap-to-pixel:true;");

    // Fix #3: Prevent fractional stage dimensions
    stage.widthProperty().addListener((obs, oldV, newV) ->
        stage.setWidth(Math.floor(newV.doubleValue()))
    );
    stage.heightProperty().addListener((obs, oldV, newV) ->
        stage.setHeight(Math.floor(newV.doubleValue()))
    );

    // -----------------------------------------------

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

            // Split on TAB instead of comma
            String[] cols = line.split("\t");

            // Find WKT column (usually last)
            String rawWkt = null;
            for (String c : cols) {
                if (c.contains("MULTILINESTRING")) {
                    rawWkt = c;
                    break;
                }
            }
            if (rawWkt == null) continue; // skip broken rows

            String wkt = rawWkt.trim();

            // Find kote (depends on your file)
            int kote;
            try {
                kote = Integer.parseInt(cols[4]);  // adjust if column changes
            } catch (Exception e) {
                kote = 0;
            }

          try {
    GeometryFactory gf = new GeometryFactory();
    WKTReader reader = new WKTReader(gf);

    Geometry geom = reader.read(wkt);

    int numGeom = geom.getNumGeometries();

    for (int gIndex = 0; gIndex < numGeom; gIndex++) {

        Geometry part = geom.getGeometryN(gIndex);
        if (!(part instanceof LineString)) continue;

        LineString lineString = (LineString) part;
        Coordinate[] coords = lineString.getCoordinates();

       StringBuilder js = new StringBuilder("addLine([");

for (int i = 0; i < coords.length; i++) {
    Coordinate c = coords[i];
    double[] latlon = toLatLon(c.x, c.y);
    js.append("[").append(latlon[0]).append(",").append(latlon[1]).append("]");
    if (i < coords.length - 1) js.append(",");
}

js.append("], ").append(kote).append(");");



        Platform.runLater(() -> engine.executeScript(js.toString()));
    }

} catch (Exception e) {
    e.printStackTrace();
}


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
