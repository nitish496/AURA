package dev.lilt.player;

/** Small palette sampler, with a strict dark-channel ceiling for readable controls. */
public final class ArtworkTheme {
    private ArtworkTheme() {}
    public static int playerColor(int[] pixels){
        int c=color(pixels);if(c==0xff111315)return 0xff000000;
        int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;float scale=108f/Math.max(r,Math.max(g,b));
        return 0xff000000|(Math.round(r*scale)<<16)|(Math.round(g*scale)<<8)|Math.round(b*scale);
    }
    public static int color(int[] pixels){
        int[] weights=new int[512],red=new int[512],green=new int[512],blue=new int[512];int best=-1;
        for(int p:pixels){int alpha=p>>>24;if(alpha<128)continue;int r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));if(max<24)continue;
            int bucket=(r>>5)*64+(g>>5)*8+(b>>5),weight=1+(max-min)/32;weights[bucket]+=weight;red[bucket]+=r*weight;green[bucket]+=g*weight;blue[bucket]+=b*weight;
        }
        for(int i=0;i<512;i++)if(weights[i]>0&&(best<0||weights[i]>weights[best]))best=i;
        if(best<0)return 0xff111315;
        int r=red[best]/weights[best],g=green[best]/weights[best],b=blue[best]/weights[best];float scale=Math.min(.40f,64f/Math.max(r,Math.max(g,b)));
        return 0xff000000|(Math.round(r*scale)<<16)|(Math.round(g*scale)<<8)|Math.round(b*scale);
    }
}
