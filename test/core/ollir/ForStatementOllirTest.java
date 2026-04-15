package core.ollir;

import org.junit.Test;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.inst.CondBranchInstruction;
import org.specs.comp.ollir.inst.GotoInstruction;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class ForStatementOllirTest extends JmmTestEnv {

    public ForStatementOllirTest() {
        super("", "");
    }

    @Test
    public void forWithAssignmentUpdateShouldGenerateWhileLikeFlow() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    public int method(int n) {
                        int i;
                        for (i = 0; i < n; i = i + 1) {
                            i = i + 2;
                        }
                        return i;
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var condCount = method.getInstructions().stream().filter(CondBranchInstruction.class::isInstance).count();
        var gotoCount = method.getInstructions().stream().filter(GotoInstruction.class::isInstance).count();

        assertTrue("For-loop should generate at least one conditional branch", condCount >= 1);
        assertTrue("For-loop should generate gotos for loop flow", gotoCount >= 2);
    }

    @Test
    public void forWithIncUpdateShouldGenerateWhileLikeFlow() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    public int method(int n) {
                        int i;
                        for (i = 0; i < n; i++) {
                            i = i + 2;
                        }
                        return i;
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var condCount = method.getInstructions().stream().filter(CondBranchInstruction.class::isInstance).count();
        var gotoCount = method.getInstructions().stream().filter(GotoInstruction.class::isInstance).count();

        assertTrue("For-loop with ++ should generate conditional branch", condCount >= 1);
        assertTrue("For-loop with ++ should generate gotos", gotoCount >= 2);
    }

    private ClassUnit toOllirClass(String code) {
        var parserResult = parseSnippet(code);
        assertTrue("Unexpected parser errors", parserResult.getReports(ReportType.ERROR).isEmpty());

        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);
        assertTrue("Unexpected semantic errors", semantics.getReports(ReportType.ERROR).isEmpty());

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);
        var classUnit = ollirResult.getOllirClass();
        assertNotNull("Ollir class unit should not be null", classUnit);
        return classUnit;
    }
}

