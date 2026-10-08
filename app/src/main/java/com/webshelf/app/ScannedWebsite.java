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
    public static boolean contains(List<String> sites,String url) {
        String normalized=normalize(url);if(normalized==null)return false;
        for(String site:sites)if(normalized.equals(normalize(site)))return true;
        return false;
    }
}
