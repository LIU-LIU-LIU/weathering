package cc.ahaly.weathering;

import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerSet;

import java.util.Set;

public class DynmapHandler {
    private MarkerSet markerSet;
    private DynmapCommonAPI api;
    private static final String ACTIVE_REGION_PREFIX = "active_region_";
    private static final String INACTIVE_REGION_PREFIX = "inactive_region_";

    public void handleApiEnabled(DynmapCommonAPI api) {
        this.api = api;
        markerSet = api.getMarkerAPI().createMarkerSet("weathering.markerset", "Weathering Regions", null, false);
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
        double[] xPoints = {x - halfSize, x + halfSize, x + halfSize, x - halfSize, x - halfSize};
        double[] zPoints = {z - halfSize, z - halfSize, z + halfSize, z + halfSize, z - halfSize};

        // 创建区域标记
        String markerId = (isActive ? ACTIVE_REGION_PREFIX : INACTIVE_REGION_PREFIX) + x + "_" + z;
        String markerLabel = (isActive ? "活跃区域" : "不活跃区域");
        AreaMarker areaMarker = markerSet.createAreaMarker(markerId, markerLabel, false, worldName, xPoints, zPoints, false);
        if (areaMarker == null) {
            System.err.println("Failed to create area marker");
            return;
        }

        // 设置区域样式
        if (isActive) {
            areaMarker.setLineStyle(3, 1.0, 0x99FF99); // 淡绿色边框
            areaMarker.setFillStyle(0.35, 0x99FF99);   // 淡绿色填充
        } else {
            areaMarker.setLineStyle(3, 1.0, 0xFF9999); // 淡红色边框
            areaMarker.setFillStyle(0.35, 0xFF9999);   // 淡红色填充
        }
    }

    public void clearDrawnRegions() {
        if (markerSet == null) {
            System.err.println("Marker set is not initialized");
            return;
        }

        Set<AreaMarker> markers = markerSet.getAreaMarkers();
        for (AreaMarker marker : markers) {
            if (marker.getMarkerID().startsWith(ACTIVE_REGION_PREFIX) || marker.getMarkerID().startsWith(INACTIVE_REGION_PREFIX)) {
                marker.deleteMarker();
            }
        }
        System.out.println("所有活跃和不活跃区域的标记已清除。");
    }
}
