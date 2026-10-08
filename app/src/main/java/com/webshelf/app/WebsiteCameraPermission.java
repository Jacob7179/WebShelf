package com.webshelf.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.function.Supplier;

/** Grants only camera access, after site consent and Android runtime consent. */
final class WebsiteCameraPermission {
    private static final int REQUEST_CAMERA=401;
    private final Activity activity;
    private final Supplier<String> currentUrl;
    private PermissionRequest pending;
    private AlertDialog prompt;
    private boolean runtimePending;
    WebsiteCameraPermission(Activity activity,Supplier<String> currentUrl){this.activity=activity;this.currentUrl=currentUrl;}
    private boolean samePage(PermissionRequest request){
        try{
            URI origin=new URI(request.getOrigin().toString()),page=new URI(currentUrl.get());
            return "https".equalsIgnoreCase(origin.getScheme())&&"https".equalsIgnoreCase(page.getScheme())
                &&origin.getHost()!=null&&origin.getHost().equalsIgnoreCase(page.getHost())
                &&port(origin)==port(page);
        }catch(Exception e){return false;}
    }
    private static int port(URI uri){return uri.getPort()==-1?443:uri.getPort();}
    void request(PermissionRequest request){
        if(activity.isFinishing()||activity.isDestroyed()||pending!=null||runtimePending||!samePage(request)
            ||!Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)){
            request.deny();return;
        }
        pending=request;
        prompt=new AlertDialog.Builder(activity).setTitle(R.string.website_camera_title)
            .setMessage(activity.getString(R.string.website_camera_question,request.getOrigin().toString()))
            .setPositiveButton(R.string.website_camera_allow,(d,w)->{
                prompt=null;if(pending!=request)return;
                if(!samePage(request)){navigation();return;}
                if(activity.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)finish(true);
                else {runtimePending=true;activity.requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA);}
            })
            .setNegativeButton(R.string.cancel,(d,w)->finish(false))
            .setOnCancelListener(d->finish(false)).create();
        prompt.show();
    }
    void result(int code){
        if(code!=REQUEST_CAMERA)return;
        runtimePending=false;
        finish(activity.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED);
    }
    private void finish(boolean allow){
        PermissionRequest request=pending;pending=null;
        if(prompt!=null){prompt.dismiss();prompt=null;}
        if(request==null)return;
        if(allow&&!activity.isFinishing()&&!activity.isDestroyed()&&samePage(request))
            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        else request.deny();
    }
    void cancel(PermissionRequest request){
        if(pending!=request)return;
        pending=null;if(prompt!=null){prompt.dismiss();prompt=null;}
    }
    void navigation(){finish(false);}
}
