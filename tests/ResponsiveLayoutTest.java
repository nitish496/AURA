package dev.lilt.player;
public final class ResponsiveLayoutTest {
    public static void main(String[] args){
        int checks=0;
        for(int width:new int[]{240,280,320,393,600,720,840,1280}){
            int content=ResponsiveLayout.contentWidth(width);
            if(content>width||content>720)throw new AssertionError("content overflow "+width);
            int columns=ResponsiveLayout.albumColumns(content);
            if((content-48-12*(columns-1))/columns<96)throw new AssertionError("album too narrow "+width);
            for(int height:new int[]{240,400,800,1200}){
                int player=ResponsiveLayout.playerWidth(content,height);
                if(player>content-48||player>340||player<=0)throw new AssertionError("player overflow "+width);
                checks++;
            }
            checks+=2;
        }
        System.out.println(checks+" responsive layout checks passed");
    }
}
