package dev.lilt.player;

/** Bounded pixel buckets keep shelf covers out of the full-player cache. */
public final class ArtworkSizing {
    private ArtworkSizing() {}
    public static int bucket(int pixels) {
        for(int size:new int[]{128,256,384,512})if(pixels<=size)return size;
        return 900;
    }
    public static int squareBytes(int pixels){int size=bucket(pixels);return size*size*4;}
}
