package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class ForLoopConditionTest extends JmmTestEnv {

    public ForLoopConditionTest() {
        super("", "");
    }

    @Test
    public void validRelationalConditionShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int i;
                        for(i = 0; i < 10; i = i + 1) {
                            return 1;
                        }
                        return 0;
                    }
                }""");

        assertEquals("Should have 0 errors", 0, conditionErrors(semantics).size());
    }

    @Test
    public void validBooleanLiteralConditionShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        for(; true ;) {
                            return 1;
                        }
                        return 0;
                    }
                }""");

        assertEquals("Should have 0 errors", 0, conditionErrors(semantics).size());
    }

    @Test
    public void validEmptyConditionShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int i;
                        // The condition is optional in the grammar, so this should not crash or error
                        for(i = 0; ; i = i + 1) {
                            return 1;
                        }
                        return 0;
                    }
                }""");

        assertEquals("Should have 0 errors", 0, conditionErrors(semantics).size());
    }

    @Test
    public void invalidIntConditionShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int i;
                        for(i = 0; 5; i = i + 1) {
                            return 1;
                        }
                        return 0;
                    }
                }""");

        var errors = conditionErrors(semantics);
        assertEquals("Should have 1 error", 1, errors.size());
    }

    @Test
    public void invalidObjectConditionShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        A obj;
                        obj = new A();
                        for(; obj ;) {
                            return 1;
                        }
                        return 0;
                    }
                }""");

        var errors = conditionErrors(semantics);
        assertEquals("Should have 1 error", 1, errors.size());
    }

    @Test
    public void invalidArithmeticConditionShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int i;
                        for(i = 0; i + 5; i = i + 1) {
                            return 1;
                        }
                        return 0;
                    }
                }""");

        var errors = conditionErrors(semantics);
        assertEquals("Should have 1 error", 1, errors.size());
    }

    private JmmSemanticsResult analyzeSnippet(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(symbolTableResult);
    }

    private List<Report> conditionErrors(JmmSemanticsResult semantics) {
        return semantics.getReports(ReportType.ERROR).stream()
                .filter(r -> r.getMessage().contains("Condition expression must be of type boolean"))
                .toList();
    }
}