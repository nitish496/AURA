package dev.lilt.player;
public final class CrossfadeRulesTest {
    private static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("case "+checks);}
    public static void main(String[] args){
        check(CrossfadeRules.validSeconds(0));check(CrossfadeRules.validSeconds(12));
        check(!CrossfadeRules.validSeconds(-1));check(!CrossfadeRules.validSeconds(13));
        check(CrossfadeRules.durationMillis(0,30000,30000)==0);
        check(CrossfadeRules.durationMillis(12,30000,30000)==12000);
        check(CrossfadeRules.durationMillis(12,2000,30000)==1000);
        check(CrossfadeRules.durationMillis(12,30000,2000)==1000);
        check(CrossfadeRules.durationMillis(12,-1,30000)==0);
        check(CrossfadeRules.incomingGain(-100,1000)==0);
        check(CrossfadeRules.incomingGain(500,1000)==.5f);
        check(CrossfadeRules.outgoingGain(500,1000)==.5f);
        check(CrossfadeRules.incomingGain(1100,1000)==1);
        check(CrossfadeRules.outgoingGain(1100,1000)==0);
        check(CrossfadeRules.incomingGain(0,0)==1);
        check(CrossfadeRules.safeFadeMillis(3,30000,30000,3350)==3000);
        check(CrossfadeRules.safeFadeMillis(3,30000,30000,2000)==1650);
        check(CrossfadeRules.safeFadeMillis(3,30000,30000,300)==0);
        check(CrossfadeRules.safeFadeMillis(0,30000,30000,10000)==0);
        check(CrossfadeRules.safeFadeMillis(12,30000,2000,15000)==1000);
        float previousIn=0,previousOut=1;
        for(int elapsed=0;elapsed<=3000;elapsed+=100){
            float in=CrossfadeRules.incomingGain(elapsed,3000),out=CrossfadeRules.outgoingGain(elapsed,3000);
            if(in<previousIn||out>previousOut||Math.abs(in+out-1)>0.00001f)throw new AssertionError("gain continuity");
            previousIn=in;previousOut=out;
        }
        check(previousIn==1 && previousOut==0);
        System.out.println("CrossfadeRulesTest: "+checks+" checks passed");
    }
}
