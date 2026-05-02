package cp2.extensions.ollir.optimization;

import org.junit.Test;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.inst.GetFieldInstruction;
import pt.up.fe.comp.test.env.OllirTestEnv;
import pt.up.fe.comp2026.ConfigOptions;

import java.util.Map;

public class PropWithSideEffectsTest extends OllirTestEnv {

    // Points to your private test directory
    private static final String BASE_PATH = "cp2/extensions/ollir/optimization/jmm/";
    private static final String RESOURCES_LOCATION = "test";

    public PropWithSideEffectsTest() {
        super(BASE_PATH, RESOURCES_LOCATION);
    }

    @Test
    public void testSideEffectsOnFields() {
        // 1. Enable optimizations
        setConfig(Map.of(ConfigOptions.getOptimize(), "true"));

        // 2. Compile your private JMM file to OLLIR
        var ollirResult = jmmToOllir("PropWithSideEffects.jmm", true);
        var classUnit = ollirResult.getOllirClass();

        // 3. Find the 'readAfterCall' method
        Method method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("readAfterCall"))
                .findFirst()
                .orElseThrow();

        // 4. Use OllirTestEnv's built-in helper to find GetField instructions!
        var fieldReads = assertInstExists(GetFieldInstruction.class, method);

        // 5. Assert that the compiler actually reads the field instead of returning 1
        assertTrue("Expected the return statement to read the field 'f' from memory, but no GetFieldInstruction was found.", fieldReads.size() > 0);
    }
}