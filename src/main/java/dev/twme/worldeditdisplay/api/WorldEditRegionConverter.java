package dev.twme.worldeditdisplay.api;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import dev.twme.worldeditdisplay.player.PlayerData;
import dev.twme.worldeditdisplay.region.RegionType;

/**
 * Converts WorldEdit regions into internal WorldEditDisplay region models for rendering.
 */
public final class WorldEditRegionConverter {
    private WorldEditRegionConverter() {}

    public static dev.twme.worldeditdisplay.region.Region convert(PlayerData playerData, Region region) throws RegionConversionException {
        if (region instanceof CuboidRegion cuboid) return convertCuboid(playerData, cuboid);
        if (region instanceof Polygonal2DRegion poly) return convertPolygon(playerData, poly);
        if (region instanceof EllipsoidRegion ellipsoid) return convertEllipsoid(playerData, ellipsoid);
        if (region instanceof CylinderRegion cylinder) return convertCylinder(playerData, cylinder);
        throw new RegionConversionException("Unsupported region type: " + region.getClass().getSimpleName());
    }

    private static dev.twme.worldeditdisplay.region.Region convertCuboid(PlayerData playerData, CuboidRegion region) {
        dev.twme.worldeditdisplay.region.CuboidRegion cuboid = (dev.twme.worldeditdisplay.region.CuboidRegion) RegionType.CUBOID.createRegion(playerData);
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        cuboid.setCuboidPoint(0, min.getX(), min.getY(), min.getZ());
        cuboid.setCuboidPoint(1, max.getX(), max.getY(), max.getZ());
        return cuboid;
    }

    private static dev.twme.worldeditdisplay.region.Region convertPolygon(PlayerData playerData, Polygonal2DRegion region) {
        dev.twme.worldeditdisplay.region.PolygonRegion polygon = (dev.twme.worldeditdisplay.region.PolygonRegion) RegionType.POLYGON.createRegion(playerData);
        int idx = 0;
        for (BlockVector2 point : region.getPoints()) {
            polygon.setPolygonPoint(idx++, point.getX(), point.getZ());
        }
        polygon.setMinMax(region.getMinimumPoint().getBlockY(), region.getMaximumPoint().getBlockY());
        return polygon;
    }

    private static dev.twme.worldeditdisplay.region.Region convertEllipsoid(PlayerData playerData, EllipsoidRegion region) {
        dev.twme.worldeditdisplay.region.EllipsoidRegion ellipsoid = (dev.twme.worldeditdisplay.region.EllipsoidRegion) RegionType.ELLIPSOID.createRegion(playerData);
        com.sk89q.worldedit.math.Vector3 center = region.getCenter();
        ellipsoid.setEllipsoidCenter((int) center.getX(), (int) center.getY(), (int) center.getZ());
        com.sk89q.worldedit.math.Vector3 radius = region.getRadius();
        ellipsoid.setEllipsoidRadii(radius.getX(), radius.getY(), radius.getZ());
        return ellipsoid;
    }

    private static dev.twme.worldeditdisplay.region.Region convertCylinder(PlayerData playerData, CylinderRegion region) {
        dev.twme.worldeditdisplay.region.CylinderRegion cylinder = (dev.twme.worldeditdisplay.region.CylinderRegion) RegionType.CYLINDER.createRegion(playerData);
        com.sk89q.worldedit.math.Vector3 center = region.getCenter();
        cylinder.setCylinderCenter((int) center.getX(), (int) center.getY(), (int) center.getZ());
        cylinder.setCylinderRadius(region.getRadius().getX(), region.getRadius().getZ());
        cylinder.setMinMax(region.getMinimumPoint().getBlockY(), region.getMaximumPoint().getBlockY());
        return cylinder;
    }

}
