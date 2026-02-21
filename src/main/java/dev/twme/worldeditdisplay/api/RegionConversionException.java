package dev.twme.worldeditdisplay.api;

/**
 * Thrown when a WorldEdit region cannot be converted to a supported display region.
 */
public class RegionConversionException extends Exception {
    public RegionConversionException(String message) {
        super(message);
    }
}
