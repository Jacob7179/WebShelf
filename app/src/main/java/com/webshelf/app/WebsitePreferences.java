package com.webshelf.app;

/** Creates and updates website preference values in localStorage. */
public final class WebsitePreferences {
    private WebsitePreferences() {}

    public static String script(String language, String appearance) {
        return script(language,appearance,"wip-theme-mode","auto","on","off","wip-language","en","zh-cn","ms","ja","ko",false);
    }

    public static String script(String language,String appearance,
                                String themeKey,String themeAuto,String themeDark,String themeLight,
                                String languageKey,String languageEn,String languageZh,String languageMs,
                                boolean forceCreate) {
        return script(language,appearance,themeKey,themeAuto,themeDark,themeLight,languageKey,languageEn,languageZh,languageMs,"ja","ko",forceCreate);
    }

    public static String script(String language,String appearance,
                                String themeKey,String themeAuto,String themeDark,String themeLight,
                                String languageKey,String languageEn,String languageZh,String languageMs,String languageJa,String languageKo,
                                boolean forceCreate) {
        String webLanguage="zh-Hans".equals(language)?languageZh:"ms".equals(language)?languageMs:"ja".equals(language)?languageJa:"ko".equals(language)?languageKo:languageEn;
        String mode="system".equals(appearance)?themeAuto:"dark".equals(appearance)?themeDark:themeLight;
        return "(function(){try{"
            + "var h=location.hostname.toLowerCase().replace(/\\.$/,'');"
            + "if(!/^https?:$/.test(location.protocol))return false;"
            + "var ngrok=['ngrok-free.dev','ngrok-free.app','ngrok.dev','ngrok.app','ngrok.io'].some(function(d){return h.endsWith('.'+d);});"
            + "var force="+forceCreate+";var changed=false;var values={};"
            + "values["+quote(languageKey)+"]="+quote(webLanguage)+";values["+quote(themeKey)+"]="+quote(mode)+";"
            + "var updates=[];Object.keys(values).forEach(function(k){if(!k)return;var old=localStorage.getItem(k);if((force||ngrok||old!==null)&&old!==values[k]){localStorage.setItem(k,values[k]);updates.push({key:k,oldValue:old,newValue:values[k]});changed=true;}});"
            + "updates.forEach(function(u){try{window.dispatchEvent(new StorageEvent('storage',{key:u.key,oldValue:u.oldValue,newValue:u.newValue,storageArea:localStorage,url:location.href}));}catch(e){}});"
            + "return changed;}catch(e){return false;}})();";
    }

    private static String quote(String value) {
        if(value==null)value="";
        StringBuilder out=new StringBuilder("\"");
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){
                case '\\':out.append("\\\\");break;
                case '"':out.append("\\\"");break;
                case '\n':out.append("\\n");break;
                case '\r':out.append("\\r");break;
                case '\t':out.append("\\t");break;
                default: if(c<0x20)out.append(String.format("\\u%04x",(int)c));else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
