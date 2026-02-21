package dev.twme.worldeditdisplay.api;

import org.bukkit.Material;

/**
 * Optional display overrides for externally provided regions.
 */
public class ExternalRegionOptions {
    private Material primary;
    private Material secondary;
    private Material grid;
    private Material background;
    private Double gridSpacing;

    public Material[] toColorArray() {
        return new Material[] { primary, secondary, grid, background };
    }

    public Double getGridSpacing() {
        return gridSpacing;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ExternalRegionOptions options = new ExternalRegionOptions();

        public Builder primary(Material material) {
            options.primary = material;
            return this;
        }

        public Builder secondary(Material material) {
            options.secondary = material;
            return this;
        }

        public Builder grid(Material material) {
            options.grid = material;
            return this;
        }

        public Builder background(Material material) {
            options.background = material;
            return this;
        }

        public Builder gridSpacing(Double spacing) {
            options.gridSpacing = spacing;
            return this;
        }

        public ExternalRegionOptions build() {
            return options;
        }
    }
}
