package com.webshelf.app;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Treat scanned content as data: accept only absolute HTTP(S) website URLs. */
public final class ScannedWebsite {
    private ScannedWebsite() {}
    public static String normalize(String raw) {
        if(raw==null)return null;
        try {
            String value=raw.trim();
            URI uri=new URI(value);
            String scheme=uri.getScheme(),host=uri.getHost();
            if(scheme==null||!(scheme.equalsIgnoreCase("https")||scheme.equalsIgnoreCase("http"))
                ||host==null||host.isEmpty()||uri.getRawUserInfo()!=null||uri.getPort()>65535)return null;
            for(int i=0;i<value.length();i++)if(Character.isWhitespace(value.charAt(i))||Character.isISOControl(value.charAt(i)))return null;
            int port=uri.getPort();
            if((scheme.equalsIgnoreCase("https")&&port==443)||(scheme.equalsIgnoreCase("http")&&port==80))port=-1;
            String path=uri.getRawPath();if(path==null||path.isEmpty())path="/";
            return scheme.toLowerCase(Locale.ROOT)+"://"+host.toLowerCase(Locale.ROOT)+(port<0?"":":"+port)+path
                +(uri.getRawQuery()==null?"":"?"+uri.getRawQuery())+(uri.getRawFragment()==null?"":"#"+uri.getRawFragment());
        }catch(Exception ignored){return null;}
    }
    public static String withoutScanLanguage(String url) {
        String normalized=normalize(url);if(normalized==null)return null;
        URI uri=URI.create(normalized);
        String host=uri.getHost();
        boolean projectHost=false;
        for(String domain:new String[]{"ngrok-free.dev","ngrok-free.app","ngrok.dev","ngrok.app","ngrok.io"})
            if(host.endsWith("."+domain))projectHost=true;
        String path=uri.getRawPath();
        // Only the project's recognized quick-login route supports this rewrite.
        // Unrelated scanned websites must retain their original paths.
        if(!projectHost||!path.matches("(?i)/(?:en/|zh-cn/|zh-tw/|ms/|id/|ja/|ko/)?quick-login/?"))return normalized;
        path=path.replaceFirst("(?i)^/(?:en|zh-cn|zh-tw|ms|id|ja|ko)(?=/)","");
        return uri.getScheme()+"://"+uri.getRawAuthority()+path
            +(uri.getRawQuery()==null?"":"?"+uri.getRawQuery())
            +(uri.getRawFragment()==null?"":"#"+uri.getRawFragment());
    }
    public static String withoutPageLanguage(String url) {
        String normalized=normalize(url);if(normalized==null)return url;
        URI uri=URI.create(normalized);
        String path=uri.getRawPath().replaceFirst("(?i)^/(?:en|zh-cn|zh-tw|ms|id|ja|ko)(?=/|$)","");
        if(path.isEmpty())path="/";
        return uri.getScheme()+"://"+uri.getRawAuthority()+path
            +(uri.getRawQuery()==null?"":"?"+uri.getRawQuery())
            +(uri.getRawFragment()==null?"":"#"+uri.getRawFragment());
    }
    public static boolean contains(List<String> sites,String url) {
        String normalized=normalize(url);if(normalized==null)return false;
        for(String site:sites)if(normalized.equals(normalize(site)))return true;
        return false;
    }
}

