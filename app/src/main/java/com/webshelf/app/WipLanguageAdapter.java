package com.webshelf.app;

/** Adapts the recognized WIP language engine only in WebShelf's response copy. */
public final class WipLanguageAdapter {
    private WipLanguageAdapter() {}
    public static boolean matchesUrl(String value) {
        try {
            java.net.URI uri=new java.net.URI(value);
            if(!"https".equalsIgnoreCase(uri.getScheme())&&!"http".equalsIgnoreCase(uri.getScheme()))return false;
            if(!"/static/language.js".equals(uri.getPath())||uri.getHost()==null)return false;
            String host=uri.getHost().toLowerCase(java.util.Locale.ROOT).replaceAll("\\.$","");
            for(String domain:new String[]{"ngrok-free.dev","ngrok-free.app","ngrok.dev","ngrok.app","ngrok.io"})if(host.endsWith("."+domain))return true;
        }catch(Exception ignored){}
        return false;
    }
    public static String adapt(String source) {
        String original=source;
        if(source.contains("__webshelfWipAdapter"))return source;
        source=source.replace("\r\n","\n");
        String nav="  function navigateToLanguage(language) {";
        String navEnd="\n  // machine QR";
        String init="  function init() {";
        String initEnd="\n  // Production increment-ledger";
        String englishBlock="    if (currentLanguage !== 'en') {\n      translateTitle();\n      translateTree(document.body);\n    }";
        if(!source.contains("const STORAGE_KEY = 'wip-language'")||!source.contains("window.WIPLanguage =")
            ||!source.contains(nav)||!source.contains(navEnd)||!source.contains(init)||!source.contains(initEnd)
            ||!source.contains(englishBlock)||!source.contains("if (!root || currentLanguage === 'en') return;")
            ||!source.contains("  function translateTitle() {\n    if (currentLanguage === 'en') return;"))return original;
        int start=source.indexOf(nav),end=source.indexOf(navEnd,start);
        if(end<start)return original;
        source=source.substring(0,start)+"""
              function navigateToLanguage(language) {
                const nextLanguage = resolveLanguage(language);
                saveLanguage(nextLanguage);
                applyLanguage(nextLanguage, { save: false });
                startObserver();
              }
            """+source.substring(end);
        source=source.replace("if (!root || currentLanguage === 'en') return;","if (!root) return;")
            .replace("  function translateTitle() {\n    if (currentLanguage === 'en') return;","  function translateTitle() {")
            .replace(englishBlock,"    translateTitle();\n    translateTree(document.body);");
        start=source.indexOf(init);end=source.indexOf(initEnd,start);
        if(end<start)return original;
        source=source.substring(0,start)+"""
              function init() {
                titleSource = document.title || '';
                titleLastApplied = titleSource;
                currentLanguage = readStoredLanguage() || readUrlLanguage();
                saveLanguage(currentLanguage);
                bindSelectors();
                applyLanguage(currentLanguage, { save: false, announce: false });
                startObserver();
                window.__webshelfWipAdapter = true;
                window.addEventListener('storage', (event) => {
                  if (event.key !== STORAGE_KEY || (event.storageArea && event.storageArea !== localStorage)) return;
                  const language = readStoredLanguage();
                  if (!language || language === currentLanguage) return;
                  applyLanguage(language, { save: false });
                  startObserver();
                });
              }
            """+source.substring(end);
        return source;
    }
}
