package dev.twme.worldeditdisplay.api;

import java.util.UUID;

/**
 * Identifies an externally managed rendered region for a player.
 */
public class ExternalRegionHandle {
    private final UUID playerId;
    private final UUID regionId;

    public ExternalRegionHandle(UUID playerId, UUID regionId) {
        this.playerId = playerId;
        this.regionId = regionId;
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID regionId() {
        return regionId;
    }
}
