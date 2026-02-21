package dev.twme.worldeditdisplay.display;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import dev.twme.worldeditdisplay.WorldEditDisplay;
import org.bukkit.entity.Player;

import dev.twme.worldeditdisplay.display.renderer.CuboidRenderer;
import dev.twme.worldeditdisplay.display.renderer.CylinderRenderer;
import dev.twme.worldeditdisplay.display.renderer.EllipsoidRenderer;
import dev.twme.worldeditdisplay.display.renderer.PolygonRenderer;
import dev.twme.worldeditdisplay.display.renderer.PolyhedronRenderer;
import dev.twme.worldeditdisplay.display.renderer.RegionRenderer;
import dev.twme.worldeditdisplay.player.PlayerData;
import dev.twme.worldeditdisplay.region.CuboidRegion;
import dev.twme.worldeditdisplay.region.CylinderRegion;
import dev.twme.worldeditdisplay.region.EllipsoidRegion;
import dev.twme.worldeditdisplay.region.PolygonRegion;
import dev.twme.worldeditdisplay.region.PolyhedronRegion;
import dev.twme.worldeditdisplay.region.Region;

/**
 * keeps track of player renderers
 * handles main and extra regions for players
 */
public class RenderManager {

    private final WorldEditDisplay plugin;

    private final Map<UUID, RegionRenderer> mainRenderers;
    private final Map<UUID, String> mainRegionHashes;
    private final Map<UUID, String> mainRenderedHashes;
    private final Map<UUID, Map<UUID, RegionRenderer>> multiRenderers;
    private final Map<UUID, Map<UUID, String>> multiRegionHashes;
    private final Map<UUID, Map<UUID, String>> multiRenderedHashes;
    private final Map<UUID, Map<UUID, RegionRenderer>> externalRenderers;
    private final Map<UUID, Map<UUID, Region>> externalRegions;
    private final Map<UUID, Map<UUID, String>> externalRegionHashes;
    private final Map<UUID, Map<UUID, String>> externalRenderedHashes;
    private final Map<Class<? extends Region>, Class<? extends RegionRenderer>> rendererTypes;

    public RenderManager(WorldEditDisplay plugin) {
        this.plugin = plugin;
        this.mainRenderers = new ConcurrentHashMap<>();
        this.mainRegionHashes = new ConcurrentHashMap<>();
        this.mainRenderedHashes = new ConcurrentHashMap<>();
        this.multiRenderers = new ConcurrentHashMap<>();
        this.multiRegionHashes = new ConcurrentHashMap<>();
        this.multiRenderedHashes = new ConcurrentHashMap<>();
        this.externalRenderers = new ConcurrentHashMap<>();
        this.externalRegions = new ConcurrentHashMap<>();
        this.externalRegionHashes = new ConcurrentHashMap<>();
        this.externalRenderedHashes = new ConcurrentHashMap<>();
        this.rendererTypes = new HashMap<>();

        registerRendererTypes();
        plugin.getLogger().info("RenderManager started");
    }

    private void registerRendererTypes() {
        rendererTypes.put(CuboidRegion.class, CuboidRenderer.class);
        rendererTypes.put(PolygonRegion.class, PolygonRenderer.class);
        rendererTypes.put(EllipsoidRegion.class, EllipsoidRenderer.class);
        rendererTypes.put(CylinderRegion.class, CylinderRenderer.class);
        rendererTypes.put(PolyhedronRegion.class, PolyhedronRenderer.class);

        plugin.getLogger().info("renderer types registered: " + rendererTypes.size());
    }

    /**
     * update renders for one player
     */
    public void updateRender(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerData playerData = PlayerData.getPlayerData(player);

        if (playerData == null) {
            plugin.getLogger().warning("no player data: " + player.getName());
            return;
        }

        if (!playerData.isRenderingEnabled()) {
            clearRender(playerId);
            return;
        }

        updateMainSelection(player, playerId, playerData.getSelection());
        updateMultiSelections(player, playerId, playerData.getMultiRegions());
        updateExternalSelections(player, playerId, externalRegions.getOrDefault(playerId, Map.of()));
    }

    private void updateMainSelection(Player player, UUID playerId, Region mainSelection) {
        RegionRenderer currentRenderer = mainRenderers.get(playerId);
        String targetHash = mainSelection == null ? null : computeRegionHash(mainSelection);
        String renderedHash = mainRenderedHashes.get(playerId);

        if (mainSelection == null) {
            if (currentRenderer != null) {
                currentRenderer.clear();
                mainRenderers.remove(playerId);
            }
            mainRegionHashes.remove(playerId);
            mainRenderedHashes.remove(playerId);
            return;
        }

        if (currentRenderer != null && !currentRenderer.getRegionType().equals(mainSelection.getClass())) {
            currentRenderer.clear();
            mainRenderers.remove(playerId);
            currentRenderer = null;
            renderedHash = null;
        }

        if (currentRenderer == null) {
            currentRenderer = createRenderer(player, mainSelection);
            if (currentRenderer != null) mainRenderers.put(playerId, currentRenderer);
            else {
                plugin.getLogger().warning("cannot make renderer: " + mainSelection.getClass().getSimpleName());
                return;
            }
        }

        mainRegionHashes.put(playerId, targetHash);

        if (targetHash != null && targetHash.equals(renderedHash)) {
            return; // no changes
        }

        try {
            currentRenderer.render(mainSelection);
            if (targetHash != null) mainRenderedHashes.put(playerId, targetHash);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "main render fail: " + player.getName(), e);
        }
    }

    private void updateExternalSelections(Player player, UUID playerId, Map<UUID, Region> regions) {
        Map<UUID, RegionRenderer> playerExternalRenderers = externalRenderers.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        Map<UUID, String> targetHashes = externalRegionHashes.getOrDefault(playerId, Map.of());
        Map<UUID, String> renderedHashes = externalRenderedHashes.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        playerExternalRenderers.keySet().removeIf(regionId -> {
            if (!regions.containsKey(regionId)) {
                RegionRenderer renderer = playerExternalRenderers.remove(regionId);
                if (renderer != null) renderer.clear();
                return true;
            }
            return false;
        });

        for (Map.Entry<UUID, Region> entry : regions.entrySet()) {
            UUID regionId = entry.getKey();
            Region region = entry.getValue();
            if (region == null) continue;

            String targetHash = targetHashes.get(regionId);
            String renderedHash = renderedHashes.get(regionId);

            RegionRenderer renderer = playerExternalRenderers.get(regionId);

            if (renderer != null && !renderer.getRegionType().equals(region.getClass())) {
                renderer.clear();
                playerExternalRenderers.remove(regionId);
                renderedHashes.remove(regionId);
                renderer = null;
            }

            if (renderer == null) {
                renderer = createRenderer(player, region);
                if (renderer != null) playerExternalRenderers.put(regionId, renderer);
                else {
                    plugin.getLogger().warning("cannot make external renderer: " + region.getClass().getSimpleName());
                    continue;
                }
            }

            // Skip rendering if nothing changed to avoid flicker
            if (targetHash != null && targetHash.equals(renderedHash)) {
                continue;
            }

            try {
                renderer.render(region);
                if (targetHash != null) renderedHashes.put(regionId, targetHash);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "external render fail: " + player.getName(), e);
            }
        }
    }

    private void updateMultiSelections(Player player, UUID playerId, Map<UUID, Region> multiRegions) {
        Map<UUID, RegionRenderer> playerMultiRenderers = multiRenderers.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        Map<UUID, String> targetHashes = multiRegionHashes.getOrDefault(playerId, Map.of());
        Map<UUID, String> renderedHashes = multiRenderedHashes.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        // remove old regions
        playerMultiRenderers.keySet().removeIf(regionId -> {
            if (!multiRegions.containsKey(regionId)) {
                RegionRenderer renderer = playerMultiRenderers.remove(regionId);
                if (renderer != null) renderer.clear();
                renderedHashes.remove(regionId);
                return true;
            }
            return false;
        });

        for (Map.Entry<UUID, Region> entry : multiRegions.entrySet()) {
            UUID regionId = entry.getKey();
            Region region = entry.getValue();
            if (region == null) continue;

            String targetHash = targetHashes.get(regionId);
            String renderedHash = renderedHashes.get(regionId);

            RegionRenderer renderer = playerMultiRenderers.get(regionId);

            if (renderer != null && !renderer.getRegionType().equals(region.getClass())) {
                renderer.clear();
                playerMultiRenderers.remove(regionId);
                renderedHashes.remove(regionId);
                renderer = null;
            }

            if (renderer == null) {
                renderer = createRenderer(player, region);
                if (renderer != null) playerMultiRenderers.put(regionId, renderer);
                else {
                    plugin.getLogger().warning("cannot make multi renderer: " + region.getClass().getSimpleName());
                    continue;
                }
            }

            if (targetHash != null && targetHash.equals(renderedHash)) {
                continue;
            }

            try {
                renderer.render(region);
                if (targetHash != null) renderedHashes.put(regionId, targetHash);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "multi render fail: " + player.getName(), e);
            }
        }
    }

    public void clearRender(UUID playerId) {
        RegionRenderer mainRenderer = mainRenderers.remove(playerId);
        if (mainRenderer != null) mainRenderer.clear();
        mainRegionHashes.remove(playerId);
        mainRenderedHashes.remove(playerId);

        Map<UUID, RegionRenderer> playerMultiRenderers = multiRenderers.remove(playerId);
        if (playerMultiRenderers != null) {
            playerMultiRenderers.values().forEach(RegionRenderer::clear);
            playerMultiRenderers.clear();
        }
        multiRegionHashes.remove(playerId);
        multiRenderedHashes.remove(playerId);

        Map<UUID, RegionRenderer> playerExternalRenderers = externalRenderers.remove(playerId);
        if (playerExternalRenderers != null) {
            playerExternalRenderers.values().forEach(RegionRenderer::clear);
            playerExternalRenderers.clear();
        }

        externalRegions.remove(playerId);
        externalRegionHashes.remove(playerId);
        externalRenderedHashes.remove(playerId);
    }

    public void clearAllRenders() {
        mainRenderers.values().forEach(RegionRenderer::clear);
        mainRenderers.clear();
        mainRegionHashes.clear();
        mainRenderedHashes.clear();

        multiRenderers.values().forEach(playerRenderers -> {
            playerRenderers.values().forEach(RegionRenderer::clear);
            playerRenderers.clear();
        });
        multiRenderers.clear();
        multiRegionHashes.clear();
        multiRenderedHashes.clear();

        externalRenderers.values().forEach(playerRenderers -> {
            playerRenderers.values().forEach(RegionRenderer::clear);
            playerRenderers.clear();
        });
        externalRenderers.clear();
        externalRegions.clear();
        externalRegionHashes.clear();
        externalRenderedHashes.clear();
    }

    private RegionRenderer createRenderer(Player player, Region region) {
        Class<? extends RegionRenderer> rendererClass = rendererTypes.get(region.getClass());
        if (rendererClass == null) {
            plugin.getLogger().warning("renderer not found: " + region.getClass().getSimpleName());
            return null;
        }

        try {
            var playerSettings = plugin.getPlayerSettingsManager().getSettings(player.getUniqueId());
            return rendererClass
                    .getConstructor(WorldEditDisplay.class, Player.class, dev.twme.worldeditdisplay.config.PlayerRenderSettings.class)
                    .newInstance(plugin, player, playerSettings);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "cannot create renderer: " + rendererClass.getSimpleName(), e);
            return null;
        }
    }

    public RegionRenderer getRenderer(UUID playerId) {
        return mainRenderers.get(playerId);
    }

    public boolean hasActiveRender(UUID playerId) {
        boolean hasMain = mainRenderers.containsKey(playerId);
        boolean hasMulti = multiRenderers.containsKey(playerId) && !multiRenderers.get(playerId).isEmpty();
        return hasMain || hasMulti;
    }

    public int getActiveRenderCount() {
        int mainCount = mainRenderers.size();
        int multiCount = multiRenderers.values().stream().mapToInt(Map::size).sum();
        return mainCount + multiCount;
    }

    public void shutdown() {
        plugin.getLogger().info("shutdown render manager");
        clearAllRenders();
    }

    public void refreshPlayerRenderer(Player player) {
        UUID playerId = player.getUniqueId();
        clearRender(playerId);
        updateRender(player);
        plugin.getLogger().fine("refreshed renderer for " + player.getName());
    }

    public void setExternalRegion(UUID playerId, UUID regionId, Region region) {
        externalRegions.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(regionId, region);
        externalRegionHashes.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(regionId, computeRegionHash(region));
    }

    public void removeExternalRegion(UUID playerId, UUID regionId) {
        Map<UUID, Region> map = externalRegions.get(playerId);
        if (map != null) map.remove(regionId);
        Map<UUID, RegionRenderer> renderers = externalRenderers.get(playerId);
        if (renderers != null) {
            RegionRenderer renderer = renderers.remove(regionId);
            if (renderer != null) renderer.clear();
        }
        Map<UUID, String> hashes = externalRegionHashes.get(playerId);
        if (hashes != null) hashes.remove(regionId);
        Map<UUID, String> renderedHashes = externalRenderedHashes.get(playerId);
        if (renderedHashes != null) renderedHashes.remove(regionId);
    }

    public void clearExternal(UUID playerId) {
        Map<UUID, RegionRenderer> renderers = externalRenderers.remove(playerId);
        if (renderers != null) renderers.values().forEach(RegionRenderer::clear);
        externalRegions.remove(playerId);
        externalRegionHashes.remove(playerId);
        externalRenderedHashes.remove(playerId);
    }

    private String computeRegionHash(Region region) {
        try {
            if (region instanceof dev.twme.worldeditdisplay.region.CuboidRegion cuboid) {
                var p1 = cuboid.getPoint1();
                var p2 = cuboid.getPoint2();
                if (p1 == null || p2 == null) return "cuboid-null";
                return "c:" + p1.getX() + "," + p1.getY() + "," + p1.getZ() + "|" +
                        p2.getX() + "," + p2.getY() + "," + p2.getZ();
            }
            if (region instanceof dev.twme.worldeditdisplay.region.PolygonRegion poly) {
                StringBuilder sb = new StringBuilder("p:");
                poly.getPoints().forEach(v -> sb.append(v == null ? "n" : v.getX() + ":" + v.getZ()).append(';'));
                sb.append("|").append(poly.getMinY()).append(':').append(poly.getMaxY());
                return sb.toString();
            }
            if (region instanceof dev.twme.worldeditdisplay.region.EllipsoidRegion ellipsoid) {
                var c = ellipsoid.getCenter();
                var r = ellipsoid.getRadii();
                if (c == null || r == null) return "ellipsoid-null";
                return "e:" + c.getX() + "," + c.getY() + "," + c.getZ() + "|" +
                        r.getX() + "," + r.getY() + "," + r.getZ();
            }
            if (region instanceof dev.twme.worldeditdisplay.region.CylinderRegion cyl) {
                var c = cyl.getCenter();
                if (c == null) return "cyl-null";
                return "y:" + c.getX() + "," + c.getY() + "," + c.getZ() + "|" +
                        cyl.getRadiusX() + ":" + cyl.getRadiusZ() + "|" +
                        cyl.getMinY() + ":" + cyl.getMaxY();
            }
            return region.getInfo();
        } catch (Exception e) {
            return region.getInfo();
        }
    }
}
