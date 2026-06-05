package extensions;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import pt.up.fe.comp.cp3.BaseJasminTestEnv;

@RunWith(Parameterized.class)
public class NewWithArgsJasminTest extends BaseJasminTestEnv {

    private static final String BASE_PATH = "extensions/";

    public NewWithArgsJasminTest(InputSource inputSource) {
        super(inputSource, BASE_PATH);
    }

    @Test
    public void testInstantiateWithArgs() {
        var res = toJasmin("InstantiateWithArgs");

        var ret = res.invoke("testNewWithArgs", Integer.class);

        assertEquals("method should return 10", 10, ret.returnValue());
    }
}