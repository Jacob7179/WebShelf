package com.webshelf.app;

import android.app.*;
import android.content.*;
import android.media.MediaMetadata;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.PowerManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import java.lang.ref.WeakReference;

/** User-controlled foreground mode for audio already playing in the browser WebView.
 * This service does not download, extract, or bypass restrictions on streamed audio. */
public final class WebMediaService extends Service {
    public static final String ACTION_ENABLE="com.webshelf.app.media.ENABLE";
    public static final String ACTION_UPDATE="com.webshelf.app.media.UPDATE";
    public static final String ACTION_CONTROL="com.webshelf.app.media.CONTROL";
    private static final String CHANNEL="webshelf_audio";
    private static final int NOTICE=914;
    private static WeakReference<MainActivity> activity=new WeakReference<>(null);
    public static void attach(MainActivity a){activity=new WeakReference<>(a);}
    public static void detach(MainActivity a){if(activity.get()==a)activity.clear();}
    private MediaSession session;
    private String title="WebShelf";
    private String artist="WebShelf", artworkUrl="";
    private Bitmap artwork;
    private final ExecutorService artworkWorker=Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock wakeLock;
    private int artworkGeneration=0;
    private boolean playing=false;
    private boolean available=false;
    private long positionMs=0,durationMs=0;
    private String lastDisplayedState="";
    @Override public void onCreate(){
        super.onCreate();
        NotificationChannel c=new NotificationChannel(CHANNEL,"WebShelf background audio",NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Media controls while background playback is enabled");
        getSystemService(NotificationManager.class).createNotificationChannel(c);
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WebShelf:BackgroundAudio");
        wakeLock.setReferenceCounted(false);
        session=new MediaSession(this,"WebShelfMedia");
        session.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){send("play");}
            @Override public void onPause(){send("pause");}
            @Override public void onSkipToNext(){send("nexttrack");}
            @Override public void onSkipToPrevious(){send("previoustrack");}
            @Override public void onRewind(){seekRelative(-30000L);}
            @Override public void onFastForward(){seekRelative(30000L);}
            @Override public void onSeekTo(long pos){MainActivity a=activity.get();if(a!=null)a.runOnUiThread(()->a.mediaSeekTo(pos));}
        });
        session.setActive(true);
        refresh();
    }
    private void send(String action){MainActivity a=activity.get();if(a!=null)a.runOnUiThread(()->a.mediaCommand(action));}
    private void seekRelative(long deltaMs){
        MainActivity a=activity.get();
        if(a==null || !available)return;
        long destination=Math.max(0L,positionMs+deltaMs);
        if(durationMs>0)destination=Math.min(destination,durationMs);
        final long seekMs=destination;
        a.runOnUiThread(()->a.mediaSeekTo(seekMs));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){
            String action=intent.getAction();
            if(ACTION_CONTROL.equals(action)){
                String command=intent.getStringExtra("command");
                if("seekback30".equals(command))seekRelative(-30000L);
                else if("seekforward30".equals(command))seekRelative(30000L);
                else send(command);
            }
            else if(ACTION_UPDATE.equals(action)){
                title=intent.getStringExtra("title");if(title==null||title.trim().isEmpty())title="WebShelf";
                artist=intent.getStringExtra("artist");if(artist==null||artist.trim().isEmpty())artist="WebShelf";
                setArtworkUrl(intent.getStringExtra("artwork"));
                playing=intent.getBooleanExtra("playing",false);
                available=intent.getBooleanExtra("available",false);
                positionMs=Math.max(0,intent.getLongExtra("positionMs",0));durationMs=Math.max(0,intent.getLongExtra("durationMs",0));
            }
        }
        if(wakeLock!=null){
            // Timed wake locks are less likely to be stranded after a WebView crash.
            if(playing && !wakeLock.isHeld())wakeLock.acquire(10*60*1000L);
            else if(!playing && wakeLock.isHeld())wakeLock.release();
        }
        refresh(); return START_NOT_STICKY;
    }
    private void setArtworkUrl(String url){
        if(url==null)url="";
        if(url.equals(artworkUrl))return;
        artworkUrl=url; artwork=null;
        final int generation=++artworkGeneration;
        if(!url.startsWith("https://")||url.length()>1200)return;
        final String requested=url;
        artworkWorker.execute(()->{
            HttpURLConnection conn=null;
            try{
                URL parsed=new URL(requested);
                conn=(HttpURLConnection)parsed.openConnection();
                conn.setConnectTimeout(5000);conn.setReadTimeout(5000);conn.setInstanceFollowRedirects(true);
                if(conn.getResponseCode()!=200){android.util.Log.w("WebShelfMedia","Artwork HTTP "+conn.getResponseCode());return;}
                int size=conn.getContentLength();if(size>2_000_000)return;
                byte[] bytes;
                try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n,total=0;
                    while((n=in.read(buffer))!=-1){total+=n;if(total>2_000_000)return;out.write(buffer,0,n);}
                    bytes=out.toByteArray();
                }
                BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=2;
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap!=null){new Handler(Looper.getMainLooper()).post(()->{if(generation==artworkGeneration){artwork=bitmap;refresh();}});}
            }catch(Exception e){android.util.Log.w("WebShelfMedia","Artwork fetch failed",e);}finally{if(conn!=null)conn.disconnect();}
        });
    }
    private PendingIntent control(String command,int id){
        Intent i=new Intent(this,WebMediaService.class).setAction(ACTION_CONTROL).putExtra("command",command);
        return PendingIntent.getService(this,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private void refresh(){
        String signature=title+"\u0001"+artist+"\u0001"+artworkUrl+"\u0001"+playing+"\u0001"+available+"\u0001"+(artwork!=null)+"\u0001"+(positionMs/2000)+"\u0001"+durationMs;
        if(signature.equals(lastDisplayedState))return;
        lastDisplayedState=signature;
        MediaMetadata.Builder metadata=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putString(MediaMetadata.METADATA_KEY_ARTIST,artist);
        if(durationMs>0)metadata.putLong(MediaMetadata.METADATA_KEY_DURATION,durationMs);
        if(artwork!=null){metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,artwork);metadata.putBitmap(MediaMetadata.METADATA_KEY_ART,artwork);metadata.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON,artwork);}
        session.setMetadata(metadata.build());
        long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|PlaybackState.ACTION_SEEK_TO|PlaybackState.ACTION_REWIND|PlaybackState.ACTION_FAST_FORWARD;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions).setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,positionMs,playing?1f:0f).build());
        Intent open=new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Action previous=new Notification.Action.Builder(R.drawable.ic_notif_previous,"Previous",control("previoustrack",2)).build();
        Notification.Action next=new Notification.Action.Builder(R.drawable.ic_notif_next,"Next",control("nexttrack",3)).build();
        Notification.Action toggle=new Notification.Action.Builder(playing?R.drawable.ic_notif_pause:R.drawable.ic_notif_play,playing?"Pause":"Play",control(playing?"pause":"play",1)).build();
        Notification.Action back30=new Notification.Action.Builder(R.drawable.ic_notif_rewind_30,"Back 30 seconds",control("seekback30",4)).build();
        Notification.Action forward30=new Notification.Action.Builder(R.drawable.ic_notif_forward_30,"Forward 30 seconds",control("seekforward30",5)).build();
        Notification notification=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title)
            .setContentText(available?artist:"Background mode enabled · open a site to play")
            .setLargeIcon(artwork)
            .setContentIntent(content).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true).setOngoing(true)
            .addAction(previous).addAction(back30).addAction(toggle).addAction(forward30).addAction(next)
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0,2,4))
            .build();
        startForeground(NOTICE,notification);
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();artworkWorker.shutdownNow();if(session!=null){session.setActive(false);session.release();session=null;}super.onDestroy();}
}
