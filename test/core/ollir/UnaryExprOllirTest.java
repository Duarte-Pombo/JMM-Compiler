package core.ollir;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class UnaryExprOllirTest extends JmmTestEnv {

    public UnaryExprOllirTest() {
        super("", "");
    }

    @Test
    public void unaryMinusShouldGenerateSubOperation() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    public int method() {
                        int a;
                        a = 5;
                        return -a;
                    }
                }""");
        assertTrue("Unary minus should generate a subtraction operation", ollirCode.contains("-.i32"));
    }

    @Test
    public void prefixIncrementShouldUpdateVariableAndProduceValue() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    public int method() {
                        int a;
                        a = 5;
                        return ++a;
                    }
                }""");
        assertTrue("Prefix ++ should generate an addition", ollirCode.contains("+.i32 1.i32"));
        assertTrue("Prefix ++ should keep variable update as assignment", countOccurrences(ollirCode, "a.i32 :=.i32") >= 2);
    }

    @Test
    public void prefixDecrementShouldUpdateVariableAndProduceValue() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    public int method() {
                        int a;
                        a = 5;
                        return --a;
                    }
                }""");
        assertTrue("Prefix -- should generate a subtraction", ollirCode.contains("-.i32 1.i32"));
        assertTrue("Prefix -- should keep variable update as assignment", countOccurrences(ollirCode, "a.i32 :=.i32") >= 2);
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

    private int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
