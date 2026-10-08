package dev.lilt.player;

import java.io.*;
import java.util.*;

/** Android-independent, versioned persistence format for a real playback queue. */
public final class QueueSnapshot {
    private static final int MAGIC=0x4c494c54, VERSION=1, MAX_ITEMS=2000;
    public static final class Entry {
        public final String id,uri,title,artist,album,artwork;
        public Entry(String id,String uri,String title,String artist,String album,String artwork) {
            this.id=clean(id);this.uri=clean(uri);this.title=clean(title);this.artist=clean(artist);this.album=clean(album);this.artwork=clean(artwork);
        }
    }
    public final List<Entry> entries;
    public final int index,repeat;
    public final long position;
    public final boolean shuffle;
    public QueueSnapshot(List<Entry> entries,int index,long position,boolean shuffle,int repeat) {
        if(entries.size()>MAX_ITEMS)throw new IllegalArgumentException("Queue is too large");
        this.entries=Collections.unmodifiableList(new ArrayList<>(entries));
        this.index=entries.isEmpty()?0:Math.max(0,Math.min(index,entries.size()-1));
        this.position=Math.max(0,position);this.shuffle=shuffle;this.repeat=repeat>=0&&repeat<=2?repeat:0;
    }
    private static String clean(String value) { return value==null?"":value.substring(0,Math.min(value.length(),8192)); }
    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(entries.size());out.writeInt(index);out.writeLong(position);out.writeBoolean(shuffle);out.writeInt(repeat);
        for(Entry e:entries) {out.writeUTF(e.id);out.writeUTF(e.uri);out.writeUTF(e.title);out.writeUTF(e.artist);out.writeUTF(e.album);out.writeUTF(e.artwork);}
        out.flush();return bytes.toByteArray();
    }
    public static QueueSnapshot decode(byte[] bytes) throws IOException {
        if(bytes.length>16*1024*1024)throw new IOException("Oversized queue record");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Unknown queue record");
        int count=in.readInt();if(count<0||count>MAX_ITEMS)throw new IOException("Invalid queue count");
        int index=in.readInt();long position=in.readLong();boolean shuffle=in.readBoolean();int repeat=in.readInt();
        List<Entry> entries=new ArrayList<>();for(int i=0;i<count;i++)entries.add(new Entry(in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF()));
        if(in.available()!=0)throw new IOException("Trailing queue data");
        return new QueueSnapshot(entries,index,position,shuffle,repeat);
    }
}
