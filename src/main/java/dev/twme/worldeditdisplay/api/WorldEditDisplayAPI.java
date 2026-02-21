package dev.twme.worldeditdisplay.api;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.sk89q.worldedit.regions.Region;
import dev.twme.worldeditdisplay.WorldEditDisplay;
import dev.twme.worldeditdisplay.display.RenderManager;
import dev.twme.worldeditdisplay.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Material;

/**
 * Public API for external plugins to display arbitrary WorldEdit regions to players.
 */
public class WorldEditDisplayAPI {

    private static final WorldEditDisplayAPI INSTANCE = new WorldEditDisplayAPI();

    private final Map<UUID, Map<UUID, dev.twme.worldeditdisplay.region.Region>> externalRegions = new ConcurrentHashMap<>();

    private WorldEditDisplayAPI() {}

    public static WorldEditDisplayAPI get() {
        return INSTANCE;
    }

    /**
     * Display a WorldEdit region to a player. Creates or updates a render with a persistent id.
     *
     * @param player target player
     * @param region WorldEdit region to render
     * @param options optional visual overrides (can be null)
     * @return handle containing player and region id
     */
    public ExternalRegionHandle display(Player player, Region region, ExternalRegionOptions options) throws RegionConversionException {
        if (player == null || region == null) {
            throw new IllegalArgumentException("player and region must not be null");
        }
        UUID playerId = player.getUniqueId();
        UUID regionId = UUID.randomUUID();
        dev.twme.worldeditdisplay.region.Region converted = convert(player, region, options);
        externalRegions.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(regionId, converted);
        syncToRenderManager(player, regionId, converted);
        return new ExternalRegionHandle(playerId, regionId);
    }

    /**
     * Update an existing external render with new region data.
     */
    public void update(ExternalRegionHandle handle, Region region, ExternalRegionOptions options) throws RegionConversionException {
        if (handle == null || region == null) throw new IllegalArgumentException("handle and region must not be null");
        Player player = Bukkit.getPlayer(handle.playerId());
        if (player == null) return;
        dev.twme.worldeditdisplay.region.Region converted = convert(player, region, options);
        externalRegions.computeIfAbsent(handle.playerId(), k -> new ConcurrentHashMap<>()).put(handle.regionId(), converted);
        syncToRenderManager(player, handle.regionId(), converted);
    }

    /**
     * Remove a specific external render for a player.
     */
    public void remove(ExternalRegionHandle handle) {
        if (handle == null) return;
        Map<UUID, dev.twme.worldeditdisplay.region.Region> map = externalRegions.get(handle.playerId());
        if (map != null) map.remove(handle.regionId());
        Player player = Bukkit.getPlayer(handle.playerId());
        if (player != null) getRenderManager().removeExternalRegion(player.getUniqueId(), handle.regionId());
    }

    /**
     * Clear all external renders for a player.
     */
    public void clear(Player player) {
        if (player == null) return;
        externalRegions.remove(player.getUniqueId());
        getRenderManager().clearExternal(player.getUniqueId());
    }

    private void syncToRenderManager(Player player, UUID regionId, dev.twme.worldeditdisplay.region.Region region) {
        getRenderManager().setExternalRegion(player.getUniqueId(), regionId, region);
        // Update renders without clearing the player's main/multi selection to avoid flicker
        getRenderManager().updateRender(player);
    }

    private dev.twme.worldeditdisplay.region.Region convert(Player player, Region region, ExternalRegionOptions options) throws RegionConversionException {
        PlayerData playerData = PlayerData.getPlayerData(player);
        dev.twme.worldeditdisplay.region.Region converted = WorldEditRegionConverter.convert(playerData, region);
        if (options != null) {
            if (options.getGridSpacing() != null) converted.setGridSpacing(options.getGridSpacing());
            Material[] colors = options.toColorArray();
            if (colors != null) converted.setColorMaterials(colors);
        }
        return converted;
    }

    private RenderManager getRenderManager() {
        WorldEditDisplay plugin = WorldEditDisplay.getPlugin();
        if (plugin == null) throw new IllegalStateException("WorldEditDisplay plugin not loaded");
        return plugin.getRenderManager();
    }
}
