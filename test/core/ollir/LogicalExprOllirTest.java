package core.ollir;

import org.junit.Test;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.inst.CondBranchInstruction;
import org.specs.comp.ollir.inst.GotoInstruction;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class LogicalExprOllirTest extends JmmTestEnv {

    public LogicalExprOllirTest() {
        super("", "");
    }

    @Test
    public void allLogicalOperatorsShouldBePresentInOllirCode() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    public boolean method(boolean a, boolean b, int x, int y) {
                        return !a || (a && b) || (x < y) || (x > y) || (x <= y) || (x >= y) || (x == y) || (x != y);
                    }
                }""");

        assertTrue("Should generate logical not", ollirCode.contains("!.bool"));
        assertTrue("Should generate less-than", ollirCode.contains("<.bool"));
        assertTrue("Should generate greater-than", ollirCode.contains(">.bool"));
        assertTrue("Should generate less-or-equal", ollirCode.contains("<=.bool"));
        assertTrue("Should generate greater-or-equal", ollirCode.contains(">=.bool"));
        assertTrue("Should generate equality", ollirCode.contains("==.bool"));
        assertTrue("Should generate inequality", ollirCode.contains("!=.bool"));
    }

    @Test
    public void andAndOrShouldGenerateShortCircuitFlow() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    public boolean method(boolean a, boolean b, boolean c, boolean d) {
                        return (a && b) || (c && d);
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var condCount = method.getInstructions().stream()
                .filter(CondBranchInstruction.class::isInstance)
                .count();
        var gotoCount = method.getInstructions().stream()
                .filter(GotoInstruction.class::isInstance)
                .count();

        assertTrue("Short-circuit logical expressions should generate conditionals", condCount >= 3);
        assertTrue("Short-circuit logical expressions should generate gotos", gotoCount >= 3);
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

    private String toOllirCode(String code) {
        var parserResult = parseSnippet(code);
        assertTrue("Unexpected parser errors", parserResult.getReports(ReportType.ERROR).isEmpty());

        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);
        assertTrue("Unexpected semantic errors", semantics.getReports(ReportType.ERROR).isEmpty());

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);
        assertNotNull("Ollir code should not be null", ollirResult.getOllirCode());
        return ollirResult.getOllirCode();
    }
}
