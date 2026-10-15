package com.webshelf.app;

import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/** Native Media3 player for direct, permitted HTTPS audio streams, independent of WebView. */
public final class NativeAudioService extends MediaSessionService {
    public static final String ACTION_PLAY = "com.webshelf.app.native.PLAY";
    public static final String ACTION_STOP = "com.webshelf.app.native.STOP";
    public static final String ACTION_PAUSE = "com.webshelf.app.native.PAUSE";
    public static final String ACTION_RESUME = "com.webshelf.app.native.RESUME";
    public static final String ACTION_SEEK_BACK = "com.webshelf.app.native.SEEK_BACK";
    public static final String ACTION_SEEK_FORWARD = "com.webshelf.app.native.SEEK_FORWARD";
    private ExoPlayer player;
    private MediaSession session;

    @Override public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        session = new MediaSession.Builder(this, player).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_PLAY.equals(intent.getAction())) {
            String url = intent.getStringExtra("url");
            if (url != null && url.startsWith("https://") && url.length() <= 4096) {
                String title = intent.getStringExtra("title");
                String artist = intent.getStringExtra("artist");
                String art = intent.getStringExtra("artwork");
                MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                        .setTitle(title == null || title.trim().isEmpty() ? "WebShelf audio" : title)
                        .setArtist(artist == null ? "" : artist);
                if (art != null && art.startsWith("https://") && art.length() < 4096) metadata.setArtworkUri(Uri.parse(art));
                MediaItem item = new MediaItem.Builder().setUri(url).setMediaMetadata(metadata.build()).build();
                player.setMediaItem(item);
                player.prepare();
                player.play();
            }
        } else if (intent != null && ACTION_PAUSE.equals(intent.getAction())) {
            player.pause();
        } else if (intent != null && ACTION_RESUME.equals(intent.getAction())) {
            if (player.getMediaItemCount() > 0) player.play();
        } else if (intent != null && ACTION_SEEK_BACK.equals(intent.getAction())) {
            if (player.getMediaItemCount() > 0) player.seekTo(Math.max(0L, player.getCurrentPosition() - 30000L));
        } else if (intent != null && ACTION_SEEK_FORWARD.equals(intent.getAction())) {
            if (player.getMediaItemCount() > 0) {
                long target = player.getCurrentPosition() + 30000L;
                long duration = player.getDuration();
                if (duration != C.TIME_UNSET && duration >= 0) target = Math.min(target, duration);
                player.seekTo(target);
            }
        } else if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            player.stop();
            player.clearMediaItems();
            stopSelf();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) { return session; }

    @Override public void onDestroy() {
        if (session != null) { session.release(); session = null; }
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
