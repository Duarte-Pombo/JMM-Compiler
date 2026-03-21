package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class CallsTest extends JmmTestEnv {

    public CallsTest() {
        super("", "");
    }

    @Test
    public void implicitInheritedObjectMethodShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public String m() {
                        return toString();
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void explicitInheritedObjectMethodShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public String m() {
                        return this.toString();
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void missingImplicitMethodShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        missingCall();
                        return 0;
                    }
                }""");

        var errors = errorReports(semantics);
        assertTrue("Expected at least one semantic error", !errors.isEmpty());
        assertTrue("Expected method-not-found message",
                errors.stream().anyMatch(report -> report.getMessage().contains("Method not found")));
    }

    private JmmSemanticsResult analyzeSnippet(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(symbolTableResult);
    }

    private List<Report> errorReports(JmmSemanticsResult semantics) {
        return semantics.getReports(ReportType.ERROR);
    }
}
