package dev.lilt.player.audio;

import java.util.Locale;

/** Classifies queue entries without Android types. Remote catalogue ids start with "audius:". */
public final class MediaIds {
    public static final String REMOTE_PREFIX = "audius:";
    private MediaIds() {}

    public static boolean isHttp(String uri) {
        if (uri == null) return false;
        String lower = uri.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    /** True for streamed catalogue items: an "audius:" id or an http(s) stream URI. */
    public static boolean isRemote(String mediaId, String uri) {
        return (mediaId != null && mediaId.startsWith(REMOTE_PREFIX)) || isHttp(uri);
    }

    /** Device library entries come from MediaStore and need the media read permission. */
    public static boolean isLocalMedia(String uri) {
        return uri != null && uri.startsWith("content://media/");
    }
}
