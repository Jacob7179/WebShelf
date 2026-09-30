package com.webshelf.app;

import java.net.URI;
import java.util.List;

/** Finds the bookmark that best describes the page actually displayed. */
public final class SiteMatcher {
    private SiteMatcher() {}
    public static int find(List<String> sites,String current) {
        URI page=parse(current);if(page==null)return -1;
        int best=-1,bestScore=-1,originCount=0,onlyOrigin=-1;
        String pagePath=path(page);
        for(int i=0;i<sites.size();i++) {
            URI saved=parse(sites.get(i));if(saved==null||!sameOrigin(saved,page))continue;
            originCount++;onlyOrigin=i;
            String savedPath=path(saved);int score=-1;
            if(savedPath.equals(pagePath))score=1000000+(java.util.Objects.equals(saved.getRawQuery(),page.getRawQuery())?1:0);
            else if(savedPath.equals("/")||pagePath.startsWith(savedPath+"/"))score=savedPath.length();
            if(score>bestScore){best=i;bestScore=score;}
        }
        // One bookmark for an origin can own other pages on that site; multiple
        // unrelated bookmarks should not arbitrarily receive the current page.
        return best>=0?best:originCount==1?onlyOrigin:-1;
    }
    private static URI parse(String value){
        try{URI uri=new URI(value);if(uri.getHost()!=null&&("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme())))return uri;}catch(Exception ignored){}
        return null;
    }
    private static boolean sameOrigin(URI a,URI b){return a.getScheme().equalsIgnoreCase(b.getScheme())&&a.getHost().equalsIgnoreCase(b.getHost())&&port(a)==port(b);}
    private static int port(URI uri){return uri.getPort()!=-1?uri.getPort():"https".equalsIgnoreCase(uri.getScheme())?443:80;}
    private static String path(URI uri){String p=uri.getRawPath();if(p==null||p.isEmpty())return "/";while(p.length()>1&&p.endsWith("/"))p=p.substring(0,p.length()-1);return p;}
}
