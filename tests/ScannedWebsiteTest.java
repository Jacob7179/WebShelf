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
        System.out.println("ScannedWebsite tests passed");
    }
}
