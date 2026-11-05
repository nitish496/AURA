package dev.lilt.player.stream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/** Small UTF-8 file helpers with crash-safe (write, fsync, rename) replacement. Pure Java. */
final class JsonFiles {
    private JsonFiles() {}

    private static final AtomicLong SEQUENCE = new AtomicLong(System.nanoTime() & 0xffffff);

    /** File contents, or null when the file does not exist. Throws when larger than {@code maxBytes}. */
    static String read(File file, int maxBytes) throws IOException {
        if (!file.isFile()) return null;
        if (file.length() > maxBytes) throw new IOException(file.getName() + " is larger than " + maxBytes + " bytes");
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(32, file.length()));
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > maxBytes) throw new IOException(file.getName() + " grew past " + maxBytes + " bytes");
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Replaces {@code file} atomically: readers see either the old or the new content, never a
     * torn write, even if the process dies mid-write.
     */
    static void writeAtomic(File file, String content) throws IOException {
        writeAtomic(file, content.getBytes(StandardCharsets.UTF_8));
    }

    static void writeAtomic(File file, byte[] bytes) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create " + parent.getName());
        }
        File temporary = new File(file.getPath() + "." + SEQUENCE.incrementAndGet() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(bytes);
            out.flush();
            out.getFD().sync();
        } catch (IOException failed) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw failed;
        }
        if (!temporary.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw new IOException("Cannot replace " + file.getName());
        }
    }
}
