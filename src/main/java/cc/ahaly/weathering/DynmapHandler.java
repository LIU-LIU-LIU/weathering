package cc.ahaly.weathering;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.Marker;

public class DynmapHandler {
    private MarkerSet markerSet;
    private DynmapCommonAPI api;

    public void handleApiEnabled(DynmapCommonAPI api) {
        markerSet = api.getMarkerAPI().createMarkerSet("myMarkers", "My Markers", null, false);
        if (markerSet == null) {
            System.err.println("Error creating marker set");
        }
    }

    public void drawSquareRegion(String worldName, double x, double y, double z, int size) {
        if (markerSet == null) {
            System.err.println("Marker set is not initialized");
            return;
        }

        // 计算正方形的四个顶点
        double halfSize = size / 2.0;
        double[][] points = {
                {x - halfSize, z - halfSize},
                {x + halfSize, z - halfSize},
                {x + halfSize, z + halfSize},
                {x - halfSize, z + halfSize},
                {x - halfSize, z - halfSize} // 关闭路径回到起点
        };

        // 创建标记
        for (int i = 0; i < points.length - 1; i++) {
            double[] start = points[i];
            double[] end = points[i + 1];
            String markerId = "marker_" + start[0] + "_" + start[1] + "_" + end[0] + "_" + end[1];
            String markerLabel = "活跃区域" + i;
            markerSet.createMarker(markerId, markerLabel, worldName, start[0], y, start[1], api.getMarkerAPI().getMarkerIcon("default"), false);
        }
    }
}
