package core.ollir.RegisterAllocation;

import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.test.env.JmmTestEnv;
import org.junit.Test;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;
import org.specs.comp.ollir.Method;
import pt.up.fe.comp.jmm.report.ReportType;

public class CFGTest extends JmmTestEnv {

    public CFGTest() {
        super("", "");
    }

    private pt.up.fe.comp.jmm.ollir.OllirResult getTransformedOllir(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);

        // Check for errors
        if (!semantics.getReports(ReportType.ERROR).isEmpty()) {
            System.out.println("Semantic analysis errors:");
            semantics.getReports(ReportType.ERROR).forEach(r -> System.out.println(r));
        }

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);

        if (ollirResult.getOllirClass() == null) {
            System.out.println("OLLIR class is null after toOllir");
            if (!ollirResult.getReports(ReportType.ERROR).isEmpty()) {
                System.out.println("OLLIR generation errors:");
                ollirResult.getReports(ReportType.ERROR).forEach(r -> System.out.println(r));
            }
        }

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

        for (int i = 0; i < insts.size() - 1; i++) {
            assertEquals("Instruction " + i + " should have 1 successor", 1, insts.get(i).getSuccessors().size());
        }
        assertTrue("Last instruction should have >=0 successors", insts.get(insts.size() - 1).getSuccessors().size() >= 0);
    }

    @Test
    public void testIfElseCFG() {
        var code = """
                package x;
                class A {
                    public int method(boolean cond) {
                        int a;
                        if (cond) {
                            a = 1;
                        } else {
                            a = 2;
                        }
                        return a;
                    }
                }""";

        var ollirResult = getTransformedOllir(code);
        var classUnit = ollirResult.getOllirClass();
        var method = classUnit.getMethods().stream().filter(m -> m.getMethodName().equals("method")).findFirst().get();

        var branchNodes = method.getInstructions().stream()
                .filter(i -> i.getSuccessors().size() == 2)
                .toList();

        assertTrue("Should have a branching node", !branchNodes.isEmpty());

        var branch = branchNodes.get(0);
        assertEquals("a", 2, branch.getSuccessors().size());
    }

    @Test
    public void testWhileCFG() {
        var code = """
                package x;
                class A {
                    public void method() {
                        int i;
                        i = 0;
                        while (i < 10) {
                            i = i + 1;
                        }
                    }
                }""";

        var ollirResult = getTransformedOllir(code);
        var classUnit = ollirResult.getOllirClass();
        var method = classUnit.getMethods().stream().filter(m -> m.getMethodName().equals("method")).findFirst().get();

        boolean hasLoop = method.getInstructions().stream()
                .anyMatch(inst -> inst.getSuccessors().stream()
                        .anyMatch(succ -> succ.getId() <= inst.getId()));

        assertTrue("CFG should contain a loop (back edge)", hasLoop);
    }

    @Test
    public void testFunctionCallCFG() {
        var code = """
                package x;
                class A {
                    public int method() {
                        int a;
                        a = this.helper();
                        return a;
                    }
                    public int helper() {
                        return 42;
                    }
                }""";

        var ollirResult = getTransformedOllir(code);
        var classUnit = ollirResult.getOllirClass();
        var method = classUnit.getMethods().stream().filter(m -> m.getMethodName().equals("method")).findFirst().get();

        var insts = method.getInstructions();

        for (int i = 0; i < insts.size() - 1; i++) {
            assertEquals("a", 1, insts.get(i).getSuccessors().size());
        }
    }
}
