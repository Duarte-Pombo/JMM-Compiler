package core.ollir.RegisterAllocation;

import pt.up.fe.comp.test.env.JmmTestEnv;
import org.junit.Test;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class CFGTest extends JmmTestEnv{

    public CFGTest() {
        super("", "");
    }

    private pt.up.fe.comp.jmm.ollir.OllirResult getTransformedOllir(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);
        return optimization.transformOllir(ollirResult);
    }

    @Test
    public void testSequentialCFG() {
        var code = """
                package x;
                class A {
                    public int method() {
                        int a;
                        int b;
                        a = 1;
                        b = a + 2;
                        return b;
                    }
                }""";

        var ollirResult = getTransformedOllir(code);
        var classUnit = ollirResult.getOllirClass();
        var method = classUnit.getMethods().stream().filter(m -> m.getMethodName().equals("method")).findFirst().get();

        var insts = method.getInstructions();
        // a= 1 => b= a + 2
        assertEquals("Successors of a=1", 1, insts.get(0).getSuccessors().size());
        assertEquals("Successor is b=a+2", insts.get(1), insts.get(0).getSuccessors().get(0));

        // b= a + 2 => return b
        assertEquals("Successors of b=a+2", 1, insts.get(1).getSuccessors().size());
        assertEquals("Successor is return b", insts.get(2), insts.get(1).getSuccessors().get(0));
    }
}
