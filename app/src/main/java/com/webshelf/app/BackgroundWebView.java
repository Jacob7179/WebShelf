package com.webshelf.app;

import android.content.Context;
import android.view.View;
import android.webkit.WebView;

/** Optional WebView visibility workaround for foreground-service-backed playback.
 * Does not override host site policy or guarantee that YouTube permits background audio.
 */
public final class BackgroundWebView extends WebView {
    public BackgroundWebView(Context context){super(context);}

    @Override protected void onWindowVisibilityChanged(int visibility){
        if(visibility==View.GONE && getContext() instanceof MainActivity &&
                ((MainActivity)getContext()).isBackgroundPlaybackEnabled()){
            // Avoid announcing that the window is GONE to Chromium while the user
            // has explicitly enabled background media. The Android Activity is still stopped.
            // Match the reference Capacitor WebView: do not deliver GONE to Chromium.
            return;
        }else{
            super.onWindowVisibilityChanged(visibility);
        }
    }
}
