package com.webshelf.app;

import android.webkit.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Narrow interception of the WIP JavaScript asset, never HTML or user submissions. */
public final class WipScriptInterceptor {
    private static final int MAX_BYTES=4*1024*1024;
    public WebResourceResponse intercept(WebResourceRequest request) {
        String url=request.getUrl().toString();
        if(!"GET".equals(request.getMethod())||!WipLanguageAdapter.matchesUrl(url))return null;
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)new URL(url).openConnection();
            connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);
            for(Map.Entry<String,String> h:request.getRequestHeaders().entrySet()) {
                String key=h.getKey().toLowerCase(Locale.ROOT);
                if(!Arrays.asList("host","accept-encoding","if-none-match","if-modified-since","range","cookie","connection").contains(key))connection.setRequestProperty(h.getKey(),h.getValue());
            }
            connection.setRequestProperty("Accept-Encoding","identity");
            String cookies=CookieManager.getInstance().getCookie(url);
            if(cookies!=null)connection.setRequestProperty("Cookie",cookies);
            if(connection.getResponseCode()!=200)return null;
            String contentType=connection.getContentType();
            if(contentType==null||!(contentType.contains("javascript")||contentType.contains("ecmascript")))return null;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){if(bytes.size()+count>MAX_BYTES)return null;bytes.write(buffer,0,count);}
            }
            String source=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            String adapted=WipLanguageAdapter.adapt(source);
            Map<String,String> headers=new HashMap<>();
            for(Map.Entry<String,List<String>> h:connection.getHeaderFields().entrySet()) {
                if(h.getKey()==null)continue;
                String key=h.getKey().toLowerCase(Locale.ROOT);
                if(key.equals("set-cookie")){for(String cookie:h.getValue())CookieManager.getInstance().setCookie(url,cookie);continue;}
                if(!Arrays.asList("content-length","content-encoding","etag","content-md5","transfer-encoding","connection","cache-control").contains(key))headers.put(h.getKey(),String.join(", ",h.getValue()));
            }
            headers.put("Cache-Control","no-cache");
            return new WebResourceResponse("application/javascript","UTF-8",200,"OK",headers,new ByteArrayInputStream(adapted.getBytes(StandardCharsets.UTF_8)));
        }catch(Exception ignored){return null;}
        finally{if(connection!=null)connection.disconnect();}
    }
}
