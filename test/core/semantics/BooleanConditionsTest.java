package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;


public class BooleanConditionsTest extends JmmTestEnv {

    public BooleanConditionsTest() {
        super("", "");
    }

    @Test
    public void ifWithBooleanConditionShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        if (true) {
                            return 1;
                        } else {
                            return 0;
                        }
                    }
                }""");

        assertEquals("aa",0, conditionErrors(semantics).size());
    }

    @Test
    public void ifWithIntConditionShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        if (1) {
                            return 1;
                        }
                        return 0;
                    }
                }""");

        var errors = conditionErrors(semantics);
        assertEquals("a",1, errors.size());
        assertTrue("a",errors.getFirst().getMessage().contains("boolean"));
    }

    @Test
    public void ifWithExprConditionShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        if (1+3) {
                            return 1;
                        }
                        return 0;
                    }
                }""");

        var errors = conditionErrors(semantics);
        assertEquals("a",1, errors.size());
        assertTrue("a",errors.getFirst().getMessage().contains("boolean"));
    }

    @Test
    public void arrayConditionShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int[] arr;
                        arr = new int[5];
                        if (arr) {
                            return 1;
                        }
                        return 0;
                    }
                }""");

        var errors = conditionErrors(semantics);
        assertEquals("a", 1, errors.size());
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
