package com.webshelf.app;

/** Creates preference keys on ngrok; updates only existing keys on other websites. */
public final class WebsitePreferences {
    private WebsitePreferences() {}
    public static String script(String language, String appearance) {
        String webLanguage="zh-Hans".equals(language)?"zh-cn":"ms".equals(language)?"ms":"en";
        String mode="system".equals(appearance)?"auto":"dark".equals(appearance)?"on":"off";
        return "(function(){try{"
            + "var h=location.hostname.toLowerCase().replace(/\\.$/,'');"
            + "if(!/^https?:$/.test(location.protocol))return false;"
            + "var ngrok=['ngrok-free.dev','ngrok-free.app','ngrok.dev','ngrok.app','ngrok.io'].some(function(d){return h.endsWith('.'+d);});"
            + "var changed=false;var values={'wip-language':'"+webLanguage+"','wip-theme-mode':'"+mode+"'};"
            + "var updates=[];Object.keys(values).forEach(function(k){var old=localStorage.getItem(k);if((ngrok||old!==null)&&old!==values[k]){localStorage.setItem(k,values[k]);updates.push({key:k,oldValue:old,newValue:values[k]});changed=true;}});"
            + "updates.forEach(function(u){try{window.dispatchEvent(new StorageEvent('storage',{key:u.key,oldValue:u.oldValue,newValue:u.newValue,storageArea:localStorage,url:location.href}));}catch(e){}});"
            + "return changed;}catch(e){return false;}})();";
    }
}
