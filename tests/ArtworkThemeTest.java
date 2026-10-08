package dev.lilt.player;
public final class ArtworkThemeTest {
 public static void main(String[] args){
  if(ArtworkTheme.color(new int[]{0x00ff0000})!=0xff111315)throw new AssertionError("transparent fallback");
  int red=ArtworkTheme.color(new int[]{0xffff0000,0xffff0000,0xff0000ff});if(((red>>16)&255)<=(red&255))throw new AssertionError("dominant warm artwork");
  int blue=ArtworkTheme.color(new int[]{0xff0000ff});if((blue&255)<=((blue>>16)&255))throw new AssertionError("blue artwork");
  for(int p:new int[]{0xffffffff,0xffff0000,0xff00ff00,0xff0000ff,0xffffcc00,0xff000000}){int c=ArtworkTheme.color(new int[]{p});if(((c>>16)&255)>64||((c>>8)&255)>64||(c&255)>64)throw new AssertionError("contrast safety ceiling");}
  int stronger=ArtworkTheme.playerColor(new int[]{0xff0000ff});if((stronger&255)<=(blue&255)||(stronger&255)>120)throw new AssertionError("stronger player tint with white labels");
  if(ArtworkTheme.playerColor(new int[]{0x00000000})!=0xff000000)throw new AssertionError("no art black");
  System.out.println("11 artwork-theme tests passed");
 }
}
