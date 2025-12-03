# Groundwater Viewer

A lightweight JavaFX + Leaflet application that displays large
groundwater datasets efficiently using canvas-based rendering.

## Features

-   Fast drawing of large MULTILINESTRING datasets\
-   Tile‑based CSV loading\
-   Canvas rendering for high performance\
-   Color‑coded groundwater elevations (kote)\
-   Automatic coordinate transformation (EPSG:25832 → WGS84)

## Structure

-   `map.html` -- Leaflet + canvas rendering\
-   `MapApp.java` -- JavaFX application\
-   `SplitGroundwaterCSV.java` -- Utility that splits the dataset into
    tiles\
-   `data/tiles/` -- Folder containing generated tile CSVs

## Usage

1.  Place `GroundWater.csv` in `/data/`\
2.  Run `SplitGroundwaterCSV` to generate tiles\
3.  Run `MapApp` to load the viewer

## Notes

-   Canvas rendering allows smooth panning and zooming even with large
    datasets\
-   Bounding boxes + level‑of‑detail reduce lag at low zoom levels
