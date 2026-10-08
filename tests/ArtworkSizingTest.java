package dev.lilt.player;
public final class ArtworkSizingTest {
    public static void main(String[] args){int count=0;int[][] cases={{0,128},{64,128},{128,128},{129,256},{256,256},{257,384},{384,384},{385,512},{512,512},{513,900},{4000,900}};for(int[] c:cases){if(ArtworkSizing.bucket(c[0])!=c[1])throw new AssertionError("bucket "+c[0]);count++;}if(ArtworkSizing.squareBytes(256)*90>24*1024*1024)throw new AssertionError("thumbnail budget");count++;if(ArtworkSizing.squareBytes(128)*300>24*1024*1024)throw new AssertionError("row budget");count++;System.out.println(count+" artwork sizing checks passed");}
}
