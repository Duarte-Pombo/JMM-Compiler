package core.jasmin;

import org.junit.Test;
import pt.up.fe.comp.test.env.JasminTestEnv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BranchStatementsJasminTest extends JasminTestEnv {

    private static final String BASE_PATH = "core/jasmin/branchstatements/ollir/";
    private static final String RESOURCES_LOCATION = "test";

    public BranchStatementsJasminTest() {
        super(BASE_PATH, RESOURCES_LOCATION);
    }

    private String toJasminCode(String resourceName) {
        return getJasminFromOllir(resourceName + ".ollir").getJasminCode();
    }

    @Test
    public void LessThanZeroUsesIfLt() {
        var code = toJasminCode("LessThanZero");
        assertTrue("Expected iflt for comparison against zero", code.contains("iflt"));
        assertFalse("Should not use if_icmplt when comparing against zero", code.contains("if_icmplt"));
    }

    @Test
    public void ZeroLessThanUsesIfGt() {
        var code = toJasminCode("ZeroLessThan");
        assertTrue("Expected ifgt for 0 < a comparison", code.contains("ifgt"));
        assertFalse("Should not use if_icmplt when comparing against zero", code.contains("if_icmplt"));
    }

    @Test
    public void EqualZeroUsesIfEq() {
        var code = toJasminCode("EqualZero");
        assertTrue("Expected ifeq for == 0 comparison", code.contains("ifeq"));
        assertFalse("Should not use if_icmpeq when comparing against zero", code.contains("if_icmpeq"));
    }

    @Test
    public void NotEqualZeroUsesIfNe() {
        var code = toJasminCode("NotEqualZero");
        assertTrue("Expected ifne for != 0 comparison", code.contains("ifne"));
        assertFalse("Should not use if_icmpne when comparing against zero", code.contains("if_icmpne"));
    }
}
