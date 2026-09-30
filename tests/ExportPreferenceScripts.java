import com.webshelf.app.WebsitePreferences;
import java.nio.file.*;

public class ExportPreferenceScripts {
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[0]);Files.createDirectories(output);
        for(String language:new String[]{"en","zh-Hans","ms"})
            for(String mode:new String[]{"light","dark","system"})
                Files.writeString(output.resolve(language+"-"+mode+".js"),WebsitePreferences.script(language,mode));
    }
}
