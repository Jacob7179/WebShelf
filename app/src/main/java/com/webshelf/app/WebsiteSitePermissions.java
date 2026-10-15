package com.webshelf.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

/** Website-origin consent and Android runtime permissions, both requested only on demand. */
final class WebsiteSitePermissions {
    private static final int REQUEST_MEDIA = 404, REQUEST_LOCATION = 405;
    private final Activity activity;
    private final Supplier<String> currentUrl;
    private final SharedPreferences approvals;
    private PermissionRequest media;
    private GeolocationPermissions.Callback location;
    private String locationOrigin;
    private String pendingApproval;
    private AlertDialog dialog;
    private boolean runtimePending;

    WebsiteSitePermissions(Activity activity, Supplier<String> currentUrl) {
        this.activity = activity;
        this.currentUrl = currentUrl;
        approvals = activity.getSharedPreferences("website_permissions", Activity.MODE_PRIVATE);
    }

    private static String origin(String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return null;
            int port = uri.getPort();
            return "https://" + uri.getHost().toLowerCase(Locale.ROOT) + (port < 0 || port == 443 ? "" : ":" + port);
        } catch (Exception ignored) { return null; }
    }

    private boolean valid(String requester) {
        // Subframes may legitimately request media, but both requester and current page
        // must be secure. Consent is saved for the REQUESTING origin, not the host page.
        return origin(requester) != null && origin(currentUrl.get()) != null;
    }

    private static String key(String resource, String site) { return resource + "|" + site; }
    private boolean approved(String resource, String site) { return approvals.getBoolean(key(resource, site), false); }
    private void saveApproval() {
        if (pendingApproval != null) approvals.edit().putBoolean(pendingApproval, true).apply();
        pendingApproval = null;
    }
    void clearSavedApprovals() { approvals.edit().clear().apply(); }

    void mediaRequest(PermissionRequest request) {
        String[] resources = request.getResources();
        boolean mic = Arrays.asList(resources).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        boolean camera = Arrays.asList(resources).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        String site = origin(request.getOrigin().toString());
        if ((!mic && !camera) || resources.length != (mic ? 1 : 0) + (camera ? 1 : 0)
                || media != null || location != null || dialog != null || runtimePending
                || !valid(request.getOrigin().toString()) || activity.isFinishing() || activity.isDestroyed()) {
            request.deny(); return;
        }
        media = request;
        String kind = mic && camera ? "microphone_camera" : mic ? "microphone" : "camera";
        pendingApproval = key(kind, site);
        if (approved(kind, site)) { ensureMediaRuntime(); return; }
        String usage = mic && camera ? "your microphone and camera" : mic ? "your microphone" : "your camera";
        dialog = new AlertDialog.Builder(activity)
                .setTitle("Website permission")
                .setMessage("Allow " + site + " to access " + usage + "?\n\nYour choice will be remembered. You can reset it in WebShelf Settings.")
                .setPositiveButton("Allow", (d, w) -> {
                    dialog = null;
                    if (media == request && valid(request.getOrigin().toString())) ensureMediaRuntime();
                    else finishMedia(false);
                })
                .setNegativeButton("Don't allow", (d, w) -> finishMedia(false))
                .setOnCancelListener(d -> finishMedia(false)).create();
        dialog.show();
    }

    private void ensureMediaRuntime() {
        if (media == null) return;
        ArrayList<String> missing = new ArrayList<>();
        boolean mic = Arrays.asList(media.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        boolean camera = Arrays.asList(media.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        if (mic && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.RECORD_AUDIO);
        if (camera && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.CAMERA);
        if (missing.isEmpty()) finishMedia(true);
        else { runtimePending = true; activity.requestPermissions(missing.toArray(new String[0]), REQUEST_MEDIA); }
    }

    void locationRequest(String requester, GeolocationPermissions.Callback callback) {
        String site = origin(requester);
        if (location != null || media != null || dialog != null || runtimePending || !valid(requester)
                || activity.isFinishing() || activity.isDestroyed()) {
            callback.invoke(requester, false, false); return;
        }
        location = callback;
        locationOrigin = requester;
        pendingApproval = key("location", site);
        if (approved("location", site)) { ensureLocationRuntime(); return; }
        dialog = new AlertDialog.Builder(activity)
                .setTitle("Website location permission")
                .setMessage("Allow " + site + " to access your location?\n\nYour choice will be remembered. You can reset it in WebShelf Settings.")
                .setPositiveButton("Allow", (d, w) -> {
                    dialog = null;
                    if (location != null && valid(locationOrigin)) ensureLocationRuntime();
                    else finishLocation(false);
                })
                .setNegativeButton("Don't allow", (d, w) -> finishLocation(false))
                .setOnCancelListener(d -> finishLocation(false)).create();
        dialog.show();
    }

    private boolean hasLocation() {
        return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void ensureLocationRuntime() {
        if (hasLocation()) finishLocation(true);
        else {
            runtimePending = true;
            activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
    }
    void result(int code) {
        if (code != REQUEST_MEDIA && code != REQUEST_LOCATION) return;
        runtimePending = false;
        if (code == REQUEST_LOCATION) { finishLocation(hasLocation()); return; }
        if (media == null) return;
        boolean mic = Arrays.asList(media.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        boolean camera = Arrays.asList(media.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        finishMedia((!mic || activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                && (!camera || activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED));
    }
    private void finishMedia(boolean allow) {
        PermissionRequest request = media; media = null;
        dismiss();
        boolean grant = allow && request != null && !activity.isFinishing() && !activity.isDestroyed()
                && valid(request.getOrigin().toString());
        if (grant) saveApproval(); else pendingApproval = null;
        if (request != null) {
            if (grant) request.grant(request.getResources()); else request.deny();
        }
    }
    private void finishLocation(boolean allow) {
        GeolocationPermissions.Callback callback = location;
        String requester = locationOrigin;
        location = null; locationOrigin = null;
        dismiss();
        boolean grant = allow && callback != null && !activity.isFinishing() && !activity.isDestroyed() && valid(requester);
        if (grant) saveApproval(); else pendingApproval = null;
        // Retain the web origin permission, as well as our own saved approval.
        if (callback != null) callback.invoke(requester, grant, grant);
    }
    private void dismiss() {
        if (dialog != null) { AlertDialog old = dialog; dialog = null; old.dismiss(); }
    }
    void locationCanceled() { finishLocation(false); }
    void mediaCanceled(PermissionRequest request) {
        if (media == request) { media = null; pendingApproval = null; dismiss(); }
    }
    void cancelAll() { finishMedia(false); finishLocation(false); runtimePending = false; }
}
