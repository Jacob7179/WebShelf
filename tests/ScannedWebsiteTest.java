import com.webshelf.app.ScannedWebsite;
import java.util.List;

public class ScannedWebsiteTest {
    public static void main(String[] args) {
        for(String invalid:new String[]{"", "hello", "javascript:alert(1)", "file:///tmp/a", "https://", "https://user:pass@example.com", "https://example.com/a b", "https://example.com:99999", "mailto:a@example.com"})
            if(ScannedWebsite.normalize(invalid)!=null)throw new AssertionError(invalid);
        if(ScannedWebsite.normalize(null)!=null)throw new AssertionError("null");
        String expected="https://example.com/a%20b?q=1#top";
        if(!expected.equals(ScannedWebsite.normalize(" HTTPS://EXAMPLE.COM:443/a%20b?q=1#top ")))throw new AssertionError("URL preservation");
        if(!ScannedWebsite.contains(List.of("https://Example.com"),"https://example.com:443/"))throw new AssertionError("duplicate");
        if(ScannedWebsite.contains(List.of("https://example.com/a"),"https://example.com/b"))throw new AssertionError("distinct paths");
        if(ScannedWebsite.contains(List.of("https://example.com/?a=1"),"https://example.com/?a=2"))throw new AssertionError("distinct query");
        String base="https://example.ngrok-free.dev";
        String suffix="/quick-login?next=%2Frecords#token=test%2Btoken";
        for(String language:List.of("en","zh-cn","zh-tw","ms","id","ja","ko"))
            if(!(base+suffix).equals(ScannedWebsite.withoutScanLanguage(base+"/"+language+suffix)))throw new AssertionError(language);
        if(!(base+suffix).equals(ScannedWebsite.withoutScanLanguage(base+suffix)))throw new AssertionError("unprefixed");
        if(!"https://example.com/en/quick-login#token=test".equals(ScannedWebsite.withoutScanLanguage("https://example.com/en/quick-login#token=test")))throw new AssertionError("unrelated site");
        if(!(base+"/en/other").equals(ScannedWebsite.withoutScanLanguage(base+"/en/other")))throw new AssertionError("unrelated route");
        for(String language:List.of("en","zh-cn","zh-tw","ms","id","ja","ko")){
            if(!(base+"/").equals(ScannedWebsite.withoutPageLanguage(base+"/"+language+"/")))throw new AssertionError("login destination");
            if(!(base+"/records?next=%2Fitem#section").equals(ScannedWebsite.withoutPageLanguage(base+"/"+language+"/records?next=%2Fitem#section")))throw new AssertionError("page preservation");
        }
        if(!(base+"/enough").equals(ScannedWebsite.withoutPageLanguage(base+"/enough")))throw new AssertionError("prefix boundary");
        if(!(base+"/records").equals(ScannedWebsite.withoutPageLanguage(base+"/records")))throw new AssertionError("idempotence");
        System.out.println("ScannedWebsite tests passed, including post-login destinations and token preservation");
    }
}
