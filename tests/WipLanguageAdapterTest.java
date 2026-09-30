import com.webshelf.app.WipLanguageAdapter;
import java.nio.file.*;

public class WipLanguageAdapterTest {
    public static void main(String[] args) throws Exception {
        String source=Files.readString(Path.of(args[0]));
        String adapted=WipLanguageAdapter.adapt(source);
        if(source.equals(adapted)||!adapted.contains("window.__webshelfWipAdapter = true"))throw new AssertionError("WIP script was not adapted");
        if(!adapted.equals(WipLanguageAdapter.adapt(adapted)))throw new AssertionError("Not idempotent");
        if(!"unrelated source".equals(WipLanguageAdapter.adapt("unrelated source")))throw new AssertionError("Unrelated source changed");
        String unsupported=source.replace("function init()", "function initV2()");
        if(!unsupported.equals(WipLanguageAdapter.adapt(unsupported)))throw new AssertionError("Unsupported source partially changed");
        for(String url:new String[]{"https://writing-jelly-submerge.ngrok-free.dev/static/language.js?v=1","https://demo.ngrok-free.app/static/language.js","http://demo.ngrok.io/static/language.js"})if(!WipLanguageAdapter.matchesUrl(url))throw new AssertionError(url);
        for(String url:new String[]{"https://example.com/static/language.js","https://demo.ngrok-free.dev.evil.com/static/language.js","https://evilngrok-free.dev/static/language.js","https://demo.ngrok-free.dev/static/theme.js","file:///static/language.js"})if(WipLanguageAdapter.matchesUrl(url))throw new AssertionError(url);
        Files.writeString(Path.of(args[1]),adapted);
        System.out.println("PASS: actual WIP script adapted; idempotence, unknown-source fallback, and URL scope checked");
    }
}
