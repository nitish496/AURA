package dev.lilt.player;
import java.io.*;
import java.util.*;
public final class QueueSnapshotTest {
    private static int checks;
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
    private static void rejects(byte[] bytes,String name){try{QueueSnapshot.decode(bytes);throw new AssertionError(name);}catch(IOException expected){checks++;}}
    public static void main(String[] args)throws Exception {
        QueueSnapshot.Entry entry=new QueueSnapshot.Entry("id","content://media/external/audio/media/1","Björk 🎵","Artist","Album","content://documents/cover");
        List<QueueSnapshot.Entry> entries=new ArrayList<>(Arrays.asList(entry,entry));
        QueueSnapshot restored=QueueSnapshot.decode(new QueueSnapshot(entries,1,120300,true,2).encode());
        check(restored.entries.size()==2,"repeated songs retained");check(restored.entries.get(0).title.equals(entry.title),"Unicode metadata round trip");
        check(restored.entries.get(0).artwork.equals(entry.artwork),"custom cover retained");check(restored.index==1,"duplicate selected index");check(restored.position==120300,"position retained");check(restored.shuffle&&restored.repeat==2,"modes retained");
        QueueSnapshot empty=QueueSnapshot.decode(new QueueSnapshot(Collections.emptyList(),9,-1,false,99).encode());check(empty.entries.isEmpty()&&empty.index==0&&empty.position==0&&empty.repeat==0,"empty/corrupt controls normalized");
        check(new QueueSnapshot(entries,50,-5,false,0).index==1,"index upper bound");check(new QueueSnapshot(entries,-4,0,false,0).index==0,"index lower bound");
        entries.clear();check(restored.entries.size()==2,"defensive copy");try{restored.entries.clear();throw new AssertionError("mutable snapshot");}catch(UnsupportedOperationException expected){checks++;}
        byte[] good=restored.encode();rejects(Arrays.copyOf(good,good.length-1),"truncated record");byte[] wrong=good.clone();wrong[0]=0;rejects(wrong,"wrong magic");wrong=good.clone();wrong[7]=2;rejects(wrong,"unknown version");
        wrong=good.clone();Arrays.fill(wrong,8,12,(byte)0xff);rejects(wrong,"negative count");rejects(Arrays.copyOf(good,good.length+1),"trailing bytes");
        System.out.println(checks+" queue-persistence tests passed");
    }
}
