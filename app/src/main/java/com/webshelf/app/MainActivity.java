package com.webshelf.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
    private final ArrayList<String> sites = new ArrayList<>();
    private WebView web;
    private TextView address, message;
    private ProgressBar progress;
    private LinearLayout empty;
    private String selected = "";
    private boolean failed;
    private android.content.SharedPreferences prefs;
    private int INK=Color.BLACK, MUTED=Color.BLACK, ACCENT=Color.BLACK, BG=Color.WHITE, LINE=Color.BLACK;
    private int SURFACE=Color.WHITE, SOFT=Color.WHITE, ACTIVE=Color.WHITE, ACTIVE_BORDER=Color.BLACK, ERROR=Color.BLACK, ERROR_BG=Color.WHITE, ON_ACCENT=Color.WHITE;
    private boolean dark;
    private String pendingReloadUrl;
    private final Set<String> storageReloadedUrls=new HashSet<>();
    private LinearLayout homeControl;
    private final WipScriptInterceptor wipInterceptor=new WipScriptInterceptor();
    private final Set<String> compatibilityRetried=new HashSet<>();

    private static String languageFor(Context context) {
        String saved=context.getSharedPreferences("sites",MODE_PRIVATE).getString("language", "");
        if(saved.equals("en")||saved.equals("zh-Hans")||saved.equals("ms")) return saved;
        String device=context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        return device.equals("zh")?"zh-Hans":device.equals("ms")?"ms":"en";
    }
    @Override protected void attachBaseContext(Context base) {
        android.content.res.Configuration config=new android.content.res.Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(languageFor(base)));
        String appearance=base.getSharedPreferences("sites",MODE_PRIVATE).getString("appearance","light");
        if(!appearance.equals("system")) config.uiMode=(config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK) | (appearance.equals("dark")?android.content.res.Configuration.UI_MODE_NIGHT_YES:android.content.res.Configuration.UI_MODE_NIGHT_NO);
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    private void showSettings(View anchor) {
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,getString(R.string.forward)).setEnabled(!selected.isEmpty()&&web.canGoForward());
        menu.getMenu().add(0,2,1,getString(R.string.back)).setEnabled(!selected.isEmpty()&&web.canGoBack());
        menu.getMenu().add(0,3,2,getString(R.string.add_title));
        menu.getMenu().add(0,4,3,getString(R.string.appearance));
        menu.getMenu().add(0,5,4,getString(R.string.language));
        menu.getMenu().add(0,6,5,getString(R.string.about));
        menu.setOnMenuItemClickListener(item->{switch(item.getItemId()){
            case 1: if(web.canGoForward())web.goForward();break;
            case 2: if(web.canGoBack())web.goBack();break;
            case 3: editSite(-1);break;
            case 4: showAppearancePicker();break;
            case 5: showLanguagePicker();break;
            case 6: showAbout();break;
            default:return false;
        }return true;});menu.show();
    }
    private void showAbout() {
        LinearLayout content=column();content.setPadding(dp(24),dp(16),dp(24),dp(16));
        ImageView avatar=new ImageView(this);avatar.setImageResource(R.drawable.jacob_avatar);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setContentDescription(getString(R.string.profile_image));
        avatar.setBackground(shape(SURFACE,48,0));avatar.setClipToOutline(true);
        LinearLayout.LayoutParams avatarParams=new LinearLayout.LayoutParams(dp(96),dp(96));avatarParams.bottomMargin=dp(16);content.addView(avatar,avatarParams);
        TextView name=label("Jacob7179",26,INK);name.setTypeface(null,1);content.addView(name);
        TextView github=label("https://github.com/Jacob7179",15,INK);
        github.setPaintFlags(github.getPaintFlags()|android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        github.setPadding(0,dp(12),0,dp(12));github.setMinHeight(dp(48));github.setGravity(Gravity.CENTER_VERTICAL);
        github.setBackground(touch(SURFACE,10));github.setFocusable(true);github.setContentDescription(getString(R.string.github_profile));
        github.setOnClickListener(v->{
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/Jacob7179")));}
            catch(ActivityNotFoundException e){Toast.makeText(this,getString(R.string.no_browser),Toast.LENGTH_LONG).show();}
        });content.addView(github,new LinearLayout.LayoutParams(-1,-2));
        View divider=new View(this);divider.setBackgroundColor(LINE);LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(-1,dp(1));dividerParams.setMargins(0,dp(12),0,dp(20));content.addView(divider,dividerParams);
        TextView appName=label("WebShelf",22,INK);appName.setTypeface(null,1);content.addView(appName);
        String version="";try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        TextView versionLabel=label(getString(R.string.app_version,version),13,INK);versionLabel.setPadding(0,dp(4),0,dp(14));content.addView(versionLabel);
        TextView description=label(getString(R.string.app_description),14,INK);description.setLineSpacing(dp(4),1);content.addView(description);
        TextView details=label(getString(R.string.app_details),13,INK);details.setPadding(0,dp(16),0,0);details.setLineSpacing(dp(4),1);content.addView(details);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(getString(R.string.about)).setView(scroll).setPositiveButton(getString(R.string.done),null).create();
        dialog.show();polishDialog(dialog);
    }
    private void showAppearancePicker() {
        String[] codes={"system","dark","light"};
        String[] names={getString(R.string.theme_system),getString(R.string.theme_dark),getString(R.string.theme_light)};
        String current=prefs.getString("appearance","light");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(getString(R.string.appearance))
            .setSingleChoiceItems(names,Arrays.asList(codes).indexOf(current),(d,index)->{
                d.dismiss();if(!current.equals(codes[index])){prefs.edit().putString("appearance",codes[index]).apply();recreateWithWebsitePreferences();}
            }).setNegativeButton(getString(R.string.cancel),null).create();dialog.show();polishDialog(dialog);
    }
    private void showLanguagePicker() {
        String[] codes={"en","zh-Hans","ms"};
        String[] names={"English","简体中文","Bahasa Melayu"};
        int checked=Arrays.asList(codes).indexOf(languageFor(this));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(getString(R.string.language))
            .setSingleChoiceItems(names,checked,(d,index)->{
                String next=codes[index];d.dismiss();
                // Reapply even when selected already: the site may have changed its own language.
                prefs.edit().putString("language",next).apply();recreateWithWebsitePreferences();
            }).setNegativeButton(getString(R.string.cancel),null).create();
        dialog.show();polishDialog(dialog);
    }

    @Override public void onCreate(Bundle state) {
        dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark?R.style.WebShelfDark:R.style.WebShelfLight);
        if(dark){INK=Color.WHITE;MUTED=Color.WHITE;ACCENT=Color.WHITE;BG=Color.BLACK;LINE=Color.WHITE;SURFACE=Color.BLACK;SOFT=Color.BLACK;ACTIVE=Color.BLACK;ACTIVE_BORDER=Color.WHITE;ERROR=Color.WHITE;ERROR_BG=Color.BLACK;ON_ACCENT=Color.BLACK;}
        super.onCreate(state);
        prefs = getSharedPreferences("sites", MODE_PRIVATE);
        try { JSONArray a = new JSONArray(prefs.getString("urls", "[]")); for (int i=0;i<a.length();i++) sites.add(a.getString(i)); } catch (JSONException ignored) {}
        selected = prefs.getString("selected", "");
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(SURFACE);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        LinearLayout bar = row(); bar.setPadding(dp(4),dp(4),dp(4),dp(4));
        homeControl=navItem("home",getString(R.string.home),v->{if(!selected.isEmpty())web.loadUrl(selected);});bar.addView(homeControl,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout location=row(); location.setPadding(dp(14),0,0,0); location.setBackground(shape(SURFACE,16,LINE));
        
        address=label(getString(R.string.choose_site),13,INK); address.setSingleLine(true); address.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); address.setPadding(0,0,dp(4),0); address.setTextIsSelectable(true); location.addView(address,new LinearLayout.LayoutParams(0,dp(48),1)); address.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout reload=new FrameLayout(this); reload.setContentDescription(getString(R.string.reload)); reload.setBackground(touch(Color.TRANSPARENT,14)); IconView refresh=new IconView("reload"); FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER); reload.addView(refresh,rp); reload.setOnClickListener(v->{if(!selected.isEmpty())web.reload();}); location.addView(reload,new LinearLayout.LayoutParams(dp(48),dp(48)));
        bar.addView(location,new LinearLayout.LayoutParams(0,dp(48),1));
        bar.addView(navItem("sites",getString(R.string.sites),v->showSites()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        Button settings=button("⋮",v->showSettings(v));settings.setTextSize(24);settings.setContentDescription(getString(R.string.settings));settings.setTooltipText(getString(R.string.settings));settings.setPadding(0,0,0,0);settings.setBackground(touch(Color.TRANSPARENT,12));bar.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(bar);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT)); progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(LINE)); root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));
        message = label("",13,ERROR); message.setPadding(dp(16),dp(12),dp(16),dp(12)); message.setBackgroundColor(ERROR_BG); message.setVisibility(View.GONE); root.addView(message);
        FrameLayout content = new FrameLayout(this); root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        web = new WebView(this); content.addView(web,new FrameLayout.LayoutParams(-1,-1));
        empty=column(); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(28),dp(20),dp(28),dp(20)); empty.setBackgroundColor(BG);
        IconView hero=new IconView("sites"); hero.setPadding(dp(20),dp(20),dp(20),dp(20)); hero.setBackground(shape(SOFT,24,0)); empty.addView(hero,new LinearLayout.LayoutParams(dp(80),dp(80)));
        TextView intro=label(getString(R.string.empty_title),25,INK); intro.setTypeface(null,1); intro.setGravity(Gravity.CENTER); intro.setPadding(0,dp(24),0,dp(12)); empty.addView(intro);
        TextView hint=label(getString(R.string.empty_hint),15,MUTED); hint.setGravity(Gravity.CENTER); hint.setLineSpacing(dp(4),1); empty.addView(hint);
        Button first=button(getString(R.string.first_site),v->editSite(-1)); primary(first); LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,dp(52)); fp.topMargin=dp(24); empty.addView(first,fp);
        TextView note=label(getString(R.string.local_data),12,MUTED); note.setPadding(0,dp(20),0,0); note.setGravity(Gravity.CENTER); empty.addView(note); content.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false); s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
        web.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=33) s.setAlgorithmicDarkeningAllowed(dark);
        CookieManager.getInstance().setAcceptCookie(true);
        // Service workers may fetch the same script outside WebViewClient callbacks.
        WipScriptInterceptor workerInterceptor=new WipScriptInterceptor();
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request){return workerInterceptor.intercept(request);}
        });
        web.setWebChromeClient(new WebChromeClient() { @Override public void onProgressChanged(WebView view,int value) { progress.setProgress(value); progress.setVisibility(value==100?View.INVISIBLE:View.VISIBLE); updateControls(); } });
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return wipInterceptor.intercept(request);}
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme=request.getUrl().getScheme();
                if("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)) return false;
                Toast.makeText(MainActivity.this,getString(R.string.external_link),Toast.LENGTH_SHORT).show(); return true;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) { failed=false; message.setVisibility(View.GONE); address.setText(selected.isEmpty()?getString(R.string.choose_site):url); updateControls(); }
            @Override public void onPageFinished(WebView view,String url) { address.setText(selected.isEmpty()?getString(R.string.choose_site):url); CookieManager.getInstance().flush(); if(!failed) message.setVisibility(View.GONE); updateControls(); if(!failed)syncWebsitePreferences(url); }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) { if(request.isForMainFrame()) showError(getString(R.string.load_error)); }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response) { if(request.isForMainFrame()) showError(getString(R.string.http_error, response.getStatusCode())); }
        });
        if(!sites.contains(selected)) selected=sites.isEmpty()?"":sites.get(0);
        if(!selected.isEmpty()) {
            empty.setVisibility(View.GONE);
            String reloadUrl=state==null?null:state.getString("pendingReloadUrl");
            // A settings reload must start a fresh document after localStorage has been written.
            // Restoring the old document first can run its startup code with stale settings.
            if(reloadUrl!=null) web.loadUrl(reloadUrl);
            else if(state==null || web.restoreState(state)==null) web.loadUrl(selected);
            address.setText(web.getUrl()==null?selected:web.getUrl());
        }
        else { progress.setVisibility(View.INVISIBLE); address.setText(getString(R.string.choose_site)); if(state==null) editSite(-1); }
        updateControls();
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private String websitePreferencesScript(){return WebsitePreferences.script(languageFor(this),prefs.getString("appearance","light"));}
    private void recreateWithWebsitePreferences(){
        CookieManager.getInstance().flush();String current=web.getUrl();
        if(isWebsite(current)){
            web.evaluateJavascript(websitePreferencesScript(),result->{
                if(isFinishing()||isDestroyed())return;
                // A matching stored value does not prove the visible page has applied it.
                if(current.equals(web.getUrl()))pendingReloadUrl=current;
                recreate();
            });
        }else recreate();
    }
    private boolean isWebsite(String url){if(url==null)return false;String scheme=Uri.parse(url).getScheme();return "https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme);}
    private void syncWebsitePreferences(String url){
        if(!isWebsite(url)||!url.equals(web.getUrl()))return;
        String probe="(function(){var changed="+websitePreferencesScript().replaceAll(";\\s*$","")+";return {changed:changed,wip:!!window.WIPLanguage,adapted:window.__webshelfWipAdapter===true};})()";
        web.evaluateJavascript(probe,result->{
            if(isFinishing()||isDestroyed()||!url.equals(web.getUrl()))return;
            try{
                JSONObject status=new JSONObject(result);
                Uri uri=Uri.parse(url);String origin=uri.getScheme()+"://"+uri.getEncodedAuthority();
                if(status.optBoolean("wip")&&!status.optBoolean("adapted")&&WipLanguageAdapter.matchesUrl(origin+"/static/language.js")&&compatibilityRetried.add(origin)){
                    // Evict only this asset from CacheStorage; preserve offline records and all other caches.
                    web.evaluateJavascript("(function(){var href=location.href;var finish=function(){if(location.href===href)location.reload();};if(!window.caches){finish();return;}caches.keys().then(function(names){return Promise.all(names.map(function(name){return caches.open(name).then(function(cache){return cache.keys().then(function(keys){return Promise.all(keys.filter(function(r){var u=new URL(r.url);return u.origin===location.origin&&u.pathname==='/static/language.js';}).map(function(r){return cache.delete(r);}));});});}));}).then(finish,finish);})();",null);
                }else if(status.optBoolean("changed")&&storageReloadedUrls.add(url))web.reload();
            }catch(JSONException ignored){}
        });
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
    private android.graphics.drawable.GradientDrawable shape(int color,int radius,int border){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border!=0)d.setStroke(dp(1),border);return d;}
    private android.graphics.drawable.Drawable touch(int color,int radius){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(dark?0x33ffffff:0x22000000),shape(color,radius,0),shape(Color.WHITE,radius,0));}
    private Button button(String text,View.OnClickListener action) { Button b=new Button(this); b.setText(text); b.setTextSize(14); b.setAllCaps(false); b.setTextColor(ACCENT); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setPadding(dp(16),dp(8),dp(16),dp(8)); b.setBackground(touch(SOFT,14)); b.setStateListAnimator(null); b.setOnClickListener(action); return b; }
    private void primary(Button b){b.setTextColor(ON_ACCENT);b.setBackground(touch(ACCENT,14));}
    private LinearLayout navItem(String icon,String title,View.OnClickListener action){
        LinearLayout item=column();item.setGravity(Gravity.CENTER);item.setMinimumHeight(dp(48));item.setBackground(touch(BG,12));item.setContentDescription(title);item.setTooltipText(title);item.setFocusable(true);
        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(22),dp(22));iconParams.gravity=Gravity.CENTER_HORIZONTAL;item.addView(new IconView(icon),iconParams);
        item.setOnClickListener(action);return item;
    }
    private void updateControls(){if(web==null)return;homeControl.setEnabled(!selected.isEmpty());homeControl.setAlpha(homeControl.isEnabled()?1f:0.3f);}
    private class IconView extends View {
        private final String kind; private final android.graphics.Paint paint=new android.graphics.Paint(3);
        IconView(String kind){super(MainActivity.this);this.kind=kind;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);canvas.save();canvas.translate(getPaddingLeft(),getPaddingTop());canvas.scale((getWidth()-getPaddingLeft()-getPaddingRight())/24f,(getHeight()-getPaddingTop()-getPaddingBottom())/24f);paint.setColor(ACCENT);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);android.graphics.Path p=new android.graphics.Path();
            switch(kind){
                case "back":p.moveTo(15,5);p.lineTo(8,12);p.lineTo(15,19);break;
                case "forward":p.moveTo(9,5);p.lineTo(16,12);p.lineTo(9,19);break;
                case "home":p.moveTo(3,11);p.lineTo(12,3);p.lineTo(21,11);p.moveTo(5,10);p.lineTo(5,21);p.lineTo(10,21);p.lineTo(10,14);p.lineTo(14,14);p.lineTo(14,21);p.lineTo(19,21);p.lineTo(19,10);break;
                case "reload":canvas.drawArc(4,4,20,20,45,285,false,paint);p.moveTo(20,4);p.lineTo(20,10);p.lineTo(14,10);break;
                case "sites":for(int x=3;x<=13;x+=10)for(int y=3;y<=13;y+=10)canvas.drawRoundRect(x,y,x+7,y+7,1.5f,1.5f,paint);break;
                default:canvas.drawCircle(12,12,9,paint);canvas.drawOval(8,3,16,21,paint);canvas.drawLine(3,12,21,12,paint);break;
            }canvas.drawPath(p,paint);canvas.restore();}
    }
    private void showError(String text) { failed=true; message.setText(text); message.setVisibility(View.VISIBLE); progress.setVisibility(View.INVISIBLE); }
    private void save() { prefs.edit().putString("urls",new JSONArray(sites).toString()).putString("selected",selected).apply(); updateControls(); }
    private void open(String url) { selected=url; save(); empty.setVisibility(View.GONE); web.stopLoading(); web.loadUrl(url); }
    private void showSites() {
        if(sites.isEmpty()) { editSite(-1); return; }
        String currentUrl=web.getUrl();
        final int activeIndex=SiteMatcher.find(sites,currentUrl);

        // Keep the dialog title and summary/category area fixed. Only the saved-site
        // cards scroll when the user has many sites, so the sections do not slide
        // away together with the list.
        LinearLayout content=column();
        content.setPadding(dp(16),dp(8),dp(16),dp(8));
        addSiteSummary(content,getString(R.string.current_page_title),isWebsite(currentUrl)?currentUrl:getString(R.string.choose_site),null);
        addSiteSummary(content,getString(R.string.selected_home_title),selected,getString(R.string.home_explanation));
        TextView heading=label(getString(R.string.saved_sites_title),16,INK);heading.setTypeface(null,1);heading.setPadding(dp(4),dp(12),dp(4),dp(6));content.addView(heading);
        TextView hint=label(getString(R.string.saved_sites_hint),12,MUTED);hint.setPadding(dp(4),0,dp(4),dp(10));content.addView(hint);

        LinearLayout savedList=column();
        ScrollView savedScroll=new ScrollView(this);
        savedScroll.setFillViewport(false);
        savedScroll.addView(savedList,new ScrollView.LayoutParams(-1,-2));
        int screenHeight=getResources().getDisplayMetrics().heightPixels;
        int listHeight=Math.min(dp(360),Math.max(dp(180),(int)(screenHeight*0.38f)));
        content.addView(savedScroll,new LinearLayout.LayoutParams(-1,listHeight));

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(getString(R.string.your_sites)).setView(content).setPositiveButton(getString(R.string.add_site),(d,w)->editSite(-1)).setNegativeButton(getString(R.string.done),null).create();
        for(int i=0;i<sites.size();i++){
            final int index=i;String url=sites.get(i);boolean active=i==activeIndex;boolean home=url.equals(selected);
            LinearLayout card=row();card.setPadding(dp(12),dp(10),dp(4),dp(10));card.setBackground(shape(BG,16,LINE));
            LinearLayout info=column();info.setPadding(0,dp(4),dp(8),dp(4));String host=Uri.parse(url).getHost();
            TextView title=label(host==null?url:host,16,INK);title.setTypeface(null,1);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(title);
            TextView detail=label(url,12,MUTED);detail.setMaxLines(2);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);detail.setPadding(0,dp(4),0,0);info.addView(detail);
            info.setContentDescription(getString(R.string.open_site,url));info.setBackground(touch(Color.TRANSPARENT,10));info.setOnClickListener(v->{dialog.dismiss();open(sites.get(index));});card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            if(active)addSiteBadge(info,getString(R.string.current_site_badge));
            if(home)addSiteBadge(info,getString(R.string.selected_home_badge));
            Button edit=button(getString(R.string.edit),v->{dialog.dismiss();editSite(index);});edit.setContentDescription(getString(R.string.edit_site_accessible,url));edit.setBackground(touch(Color.TRANSPARENT,10));card.addView(edit);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);savedList.addView(card,cp);
        }
        dialog.show();polishDialog(dialog);
    }
    private void addSiteBadge(LinearLayout parent,String text){TextView badge=label(text,10,ACCENT);badge.setTypeface(null,1);badge.setPadding(0,dp(6),0,0);parent.addView(badge);}
    private void addSiteSummary(LinearLayout parent,String heading,String url,String explanation){
        LinearLayout section=column();section.setPadding(dp(12),dp(12),dp(12),dp(12));section.setBackground(shape(SURFACE,16,LINE));
        TextView title=label(heading,14,INK);title.setTypeface(null,1);section.addView(title);
        TextView address=label(url,13,INK);address.setPadding(0,dp(6),0,0);address.setTextIsSelectable(true);section.addView(address);
        if(explanation!=null){TextView note=label(explanation,12,MUTED);note.setPadding(0,dp(8),0,0);section.addView(note);}
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(12);parent.addView(section,params);
    }
    private void editSite(int index) {
        LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),dp(12));TextView hint=label(getString(R.string.form_hint),14,MUTED);hint.setPadding(0,0,0,dp(20));form.addView(hint);TextView fieldLabel=label(getString(R.string.url_label),11,ACCENT);fieldLabel.setTypeface(null,1);fieldLabel.setPadding(0,0,0,dp(8));form.addView(fieldLabel);
        EditText input=new EditText(this); input.setSingleLine(true); input.setTextSize(15);input.setTextColor(INK);input.setHintTextColor(MUTED);input.setHint("https://example.com");input.setBackground(shape(BG,12,LINE));input.setContentDescription(getString(R.string.url_accessible)); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI); input.setPadding(dp(14),dp(16),dp(14),dp(16)); if(index>=0) input.setText(sites.get(index));form.addView(input,new LinearLayout.LayoutParams(-1,-2));TextView help=label(getString(R.string.scheme_hint),12,MUTED);help.setPadding(0,dp(10),0,0);form.addView(help);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(index<0?getString(R.string.add_title):getString(R.string.edit_title)).setView(form).setPositiveButton(getString(R.string.save_open),null).setNegativeButton(getString(R.string.cancel),null);
        if(index>=0) builder.setNeutralButton(getString(R.string.remove),(d,w)->new AlertDialog.Builder(this).setTitle(getString(R.string.remove_title)).setMessage(getString(R.string.remove_hint)).setPositiveButton(getString(R.string.remove),(dialog,which)->removeSite(index)).setNegativeButton(getString(R.string.cancel),null).show());
        AlertDialog dialog=builder.create(); dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String url=input.getText().toString().trim(); if(!url.contains("://")) url="https://"+url;
            Uri uri=Uri.parse(url); String scheme=uri.getScheme();
            if(!("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))||uri.getHost()==null||uri.getHost().isEmpty()||url.matches(".*\\s.*")||uri.getUserInfo()!=null) { input.setError(getString(R.string.invalid_url)); return; }
            int duplicate=sites.indexOf(url); if(duplicate>=0 && duplicate!=index) { input.setError(getString(R.string.duplicate_url)); return; }
            if(index>=0) sites.set(index,url); else sites.add(url); open(url); dialog.dismiss();
        })); dialog.show();polishDialog(dialog);
    }
    private void polishDialog(AlertDialog dialog){if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(shape(SURFACE,24,0));for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button b=dialog.getButton(which);if(b!=null){b.setAllCaps(false);b.setTextColor(which==AlertDialog.BUTTON_NEUTRAL?ERROR:ACCENT);}}}
    private void removeSite(int index) { String removed=sites.remove(index); if(removed.equals(selected)) { selected=""; if(!sites.isEmpty()) open(sites.get(0)); else { web.stopLoading(); web.loadUrl("about:blank"); empty.setVisibility(View.VISIBLE); address.setText(getString(R.string.choose_site)); message.setVisibility(View.GONE); } } save(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); if(pendingReloadUrl!=null)out.putString("pendingReloadUrl",pendingReloadUrl); }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        // Keep the live document, history, scroll position, and unsaved form data.
        // Rotation only needs new layout bounds and system-bar insets.
        getWindow().getDecorView().requestApplyInsets();
        if(web!=null){web.requestLayout();web.invalidate();}
    }
    @Override protected void onPause() { CookieManager.getInstance().flush(); web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if(web!=null) web.onResume(); }
    @Override public void onBackPressed() { if(web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
