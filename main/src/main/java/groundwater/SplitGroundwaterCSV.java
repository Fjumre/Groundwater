package groundwater;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;


public class SplitGroundwaterCSV {

    public static void splitGroundwaterCSV(String[] args) throws Exception {

        String inputFile = "data/GroundWater.csv";
        String outputDir = "data/tiles/";
        int maxLinesPerTile = 2000;   // produces ~2–3 MB chunks

        Files.createDirectories(Paths.get(outputDir));

        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {

            String header = br.readLine();   // keep header for each tile
            String line;
            int tileIndex = 0;
            int count = 0;

            PrintWriter pw = newWriter(outputDir, tileIndex, header);

            while ((line = br.readLine()) != null) {

                pw.println(line);
                count++;

                if (count >= maxLinesPerTile) {
                    pw.close();
                    tileIndex++;
                    count = 0;
                    pw = newWriter(outputDir, tileIndex, header);
                }
            }

            pw.close();
        }

        System.out.println("Finished splitting!");
    }

    private static PrintWriter newWriter(String dir, int i, String header) throws Exception {
        String filename = dir + "tile_" + i + ".csv";
        PrintWriter pw = new PrintWriter(new FileWriter(filename));
        pw.println(header);
        return pw;
    }
}
