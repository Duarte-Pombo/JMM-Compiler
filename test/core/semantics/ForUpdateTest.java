package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class ForUpdateTest extends JmmTestEnv {

    public ForUpdateTest() {
        super("", "");
    }

    @Test
    public void intIncrementInForShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        int i;
                        i = 0;
                        for (; i < 10; i++) {
                        }
                        return i;
                    }
                }""");

        assertEquals("Expected no for-update errors", 0, forUpdateErrors(semantics).size());
    }

    @Test
    public void intDecrementInForShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        int i;
                        i = 10;
                        for (; i > 0; i--) {
                        }
                        return i;
                    }
                }""");

        assertEquals("Expected no for-update errors", 0, forUpdateErrors(semantics).size());
    }

    @Test
    public void booleanIncrementInForShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        boolean b;
                        b = true;
                        for (; true; b++) {
                        }
                        return 0;
                    }
                }""");

        assertEquals("Expected one for-update error", 1, forUpdateErrors(semantics).size());
    }

    @Test
    public void booleanDecrementInForShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        boolean b;
                        b = true;
                        for (; true; b--) {
                        }
                        return 0;
                    }
                }""");

        assertEquals("Expected one for-update error", 1, forUpdateErrors(semantics).size());
    }

    private JmmSemanticsResult analyzeSnippet(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(symbolTableResult);
    }

    private List<Report> forUpdateErrors(JmmSemanticsResult semantics) {
        return semantics.getReports(ReportType.ERROR).stream()
                .filter(r -> r.getMessage().contains("For update"))
                .toList();
    }
}
