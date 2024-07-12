package cc.ahaly.weathering;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.Marker;

import java.util.Set;

public class DynmapHandler {
    private MarkerSet markerSet;
    private DynmapCommonAPI api;
    private static final String ACTIVE_REGION_PREFIX = "active_region_";
    private static final String INACTIVE_REGION_PREFIX = "inactive_region_";

    public void handleApiEnabled(DynmapCommonAPI api) {
        this.api = api;
        markerSet = api.getMarkerAPI().createMarkerSet("myMarkers", "My Markers", null, false);
        if (markerSet == null) {
            System.err.println("Error creating marker set");
        }
    }

    public void drawSquareRegion(String worldName, double x, double y, double z, int size, boolean isActive) {
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
            String markerId = (isActive ? ACTIVE_REGION_PREFIX : INACTIVE_REGION_PREFIX) + start[0] + "_" + start[1] + "_" + end[0] + "_" + end[1];
            String markerLabel = (isActive ? "活跃区域" : "不活跃区域") + i;
            markerSet.createMarker(markerId, markerLabel, worldName, start[0], y, start[1], api.getMarkerAPI().getMarkerIcon("default"), false);
        }
    }

    public void clearDrawnRegions() {
        if (markerSet == null) {
            System.err.println("Marker set is not initialized");
            return;
        }

        Set<Marker> markers = markerSet.getMarkers();
        for (Marker marker : markers) {
            if (marker.getMarkerID().startsWith(ACTIVE_REGION_PREFIX) || marker.getMarkerID().startsWith(INACTIVE_REGION_PREFIX)) {
                marker.deleteMarker();
            }
        }
        System.out.println("所有活跃和不活跃区域的标记已清除。");
    }
}
