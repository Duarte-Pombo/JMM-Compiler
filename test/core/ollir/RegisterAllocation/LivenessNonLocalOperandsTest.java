package core.ollir.RegisterAllocation;

import org.junit.Test;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.inst.Instruction;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.test.env.OllirTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;
import pt.up.fe.comp2026.optimization.RegisterAllocation.LivenessAnalyzer;

public class LivenessNonLocalOperandsTest extends OllirTestEnv {

    public LivenessNonLocalOperandsTest() {
        super("", "");
    }

    private Method getMethodFromJmm(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);

        var optimization = new JmmOptimizationImpl();
        OllirResult ollirResult = optimization.toOllir(semantics);
        ollirResult = optimization.transformOllir(ollirResult);
        ollirResult.getOllirClass().buildCFGs();

        return ollirResult.getOllirClass().getMethods().stream()
                .filter(method -> method.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();
    }

    private boolean hasUse(Method method, String name) {
        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();

        for (Instruction inst : method.getInstructions()) {
            if (analyzer.getUse(inst).contains(name)) {
                return true;
            }
        }

        return false;
    }

    @Test
    public void importedStaticCallOwnerShouldNotBeLive() {
        var method = getMethodFromJmm("""
                package x;

                import util.io;

                class A {
                    public void method() {
                        io.print(1);
                    }
                }
                """);

        assertTrue("Imported class name 'io' should not be treated as a live local variable",
                !hasUse(method, "io"));
    }

    @Test
    public void fieldNameShouldNotBeLive() {
        var method = getMethodFromJmm("""
                package x;
                class A {
                    int value;

                    public int method() {
                        int local;
                        local = this.value;
                        return local;
                    }
                }
                """);

        assertTrue("Field name 'value' should not be treated as a live local variable",
                !hasUse(method, "value"));
    }
}
