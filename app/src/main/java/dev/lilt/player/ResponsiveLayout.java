package dev.lilt.player;

/** Window-based limits: keep artwork proportional and wide screens readable. */
final class ResponsiveLayout {
    private ResponsiveLayout() {}
    static int contentWidth(int windowDp){return Math.max(1,Math.min(720,windowDp));}
    static int albumColumns(int contentDp){return Math.max(1,Math.min(4,(contentDp-48)/144));}
    static int playerWidth(int contentDp,int heightDp){
        int available=Math.max(1,contentDp-48);
        return Math.min(available,Math.min(340,Math.max(192,Math.round(heightDp*.40f))));
    }
}
