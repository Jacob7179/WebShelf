import com.webshelf.app.SiteMatcher;
import java.util.Arrays;

public class SiteMatcherTest {
    private static int count;
    private static void check(int expected,String current,String... saved){
        int actual=SiteMatcher.find(Arrays.asList(saved),current);
        if(actual!=expected)throw new AssertionError(current+": "+actual+" != "+expected);count++;
    }
    public static void main(String[] args){
        check(1,"https://www.apple.com/my/store","https://writing-jelly-submerge.ngrok-free.dev","https://www.apple.com/my/store");
        check(-1,"https://www.apple.com/my/store","https://writing-jelly-submerge.ngrok-free.dev");
        check(0,"https://demo.ngrok-free.dev/login?next=/","https://demo.ngrok-free.dev");
        check(1,"https://example.com/shop/item","https://example.com/","https://example.com/shop","https://example.com/news");
        check(1,"https://example.com/shop?q=two#anchor","https://example.com/shop?q=one","https://example.com/shop?q=two");
        check(0,"https://EXAMPLE.com:443/shop/","https://example.com/shop");
        check(-1,"https://example.com.evil.test/shop","https://example.com/shop");
        check(-1,"http://example.com/shop","https://example.com/shop");
        check(-1,"https://example.com:8443/shop","https://example.com/shop");
        check(-1,"https://example.com/shopping","https://example.com/shop","https://example.com/news");
        check(0,"https://example.com/login","https://example.com/dashboard");
        check(-1,"about:blank","https://example.com");
        check(-1,null,"https://example.com");
        check(-1,"not a URL","https://example.com");
        System.out.println("PASS: "+count+" active-site cases including the reported Apple/ngrok mismatch");
    }
}
