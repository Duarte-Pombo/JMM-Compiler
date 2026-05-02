package extensions.jmm;

import org.junit.Test;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.inst.GetFieldInstruction;
import pt.up.fe.comp.test.env.OllirTestEnv;
import pt.up.fe.comp2026.ConfigOptions;

import java.util.Map;

public class PropWithSideEffectsTest extends OllirTestEnv {

    private static final String BASE_PATH = "extensions/jmm/";
    private static final String RESOURCES_LOCATION = "test";

    public PropWithSideEffectsTest() {
        super(BASE_PATH, RESOURCES_LOCATION);
    }

    @Test
    public void testSideEffectsOnFields() {
        setConfig(Map.of(ConfigOptions.getOptimize(), "true"));

        var ollirResult = jmmToOllir("PropWithSideEffects.jmm", true);
        var classUnit = ollirResult.getOllirClass();

        Method method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("readAfterCall"))
                .findFirst()
                .orElseThrow();

        var fieldReads = assertInstExists(GetFieldInstruction.class, method);

        assertTrue("Expected the return statement to read the field 'f' from memory, but no GetFieldInstruction was found.", fieldReads.size() > 0);
    }
}