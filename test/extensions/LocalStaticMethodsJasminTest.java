package extensions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import pt.up.fe.comp.cp3.BaseJasminTestEnv;

@RunWith(Parameterized.class)
public class LocalStaticMethodsJasminTest extends BaseJasminTestEnv {

    private static final String BASE_PATH = "extensions/";

    public LocalStaticMethodsJasminTest(InputSource inputSource) {
        super(inputSource, BASE_PATH);
    }

    @Test
    public void testLocalStaticMethodCall() {
        var res = toJasmin("LocalStaticCall");

        // Supplying Integer.class so it doesn't crash expecting void!
        var ret = res.invoke("testLocalStatic", Integer.class);

        assertEquals("method should return 42", 42, ret.returnValue());
    }
}