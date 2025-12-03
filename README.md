Groundwater Visualization System

This project visualizes Danish groundwater pipe data using JavaFX, Leaflet, and a highly optimized HTML5 canvas renderer.
It supports very large datasets by splitting CSV files into tiles and rendering them efficiently in the browser.

Features

Fast rendering using <canvas> (much faster than Leaflet polylines)

Automatic CSV tiling (2000 lines per tile)

Real-time loading from Java → JavaScript (JSON batches)

Coordinate transformation (EPSG:25832 → WGS84)

Bounding-box culling for performance

Level-of-detail rendering based on zoom level

Color-coded groundwater elevation (kote)

How It Works
1. CSV → Tiles

SplitGroundwaterCSV divides the large dataset into tiles:

data/tiles/tile_0.csv
data/tiles/tile_1.csv
...

2. Java Loads Each Tile

For each tile:

WKT geometry is parsed (JTS)

Coordinates transformed (Proj4J)

Bounding box computed

JSON batch created

Sent to the web view via:

engine.executeScript("loadTile(" + json + ");");

3. HTML5 Canvas Draws All Lines

map.html collects all lines and draws them in a canvas overlay on top of the Leaflet map.

Optimizations include:

Rendering only visible lines

Skipping points when zoomed out

Redrawing using requestAnimationFrame()

Run the Project
Build:
mvn clean install

Run:
mvn javafx:run


Tiles are automatically generated if missing.

Project Structure
src/main/java/groundwater/
  MapApp.java
  SplitGroundwaterCSV.java

src/main/resources/
  map.html

data/
  GroundWater.csv
  tiles/

Dependencies

JavaFX

Leaflet.js

JTS (WKT parsing)

Proj4J (coordinate transform)

HTML5 Canvas
