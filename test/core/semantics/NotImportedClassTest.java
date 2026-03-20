package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class NotImportedClassTest extends JmmTestEnv {

    public NotImportedClassTest() {
        super("", "");
    }

    @Test
    public void instantiateImportedClassWithoutImportShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        Date d;
                        d = new Date();
                        return 0;
                    }
                }""");

        var errors = errorReports(semantics);
        assertTrue("Expected at least one semantic error", !errors.isEmpty());
        assertTrue("Expected missing import message",
                errors.stream().anyMatch(report -> report.getMessage().contains("not imported")));
    }

    @Test
    public void instantiateImportedClassWithImportShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                import java.util.Date;
                class A {
                    public int m() {
                        Date d;
                        d = new Date();
                        return 0;
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void callImportedMethodWithImportShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                import java.util.Date;
                class A {
                    public int m() {
                        Date d;
                        String s;
                        d = new Date();
                        s = d.toString();
                        return 0;
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void callUnknownImportedMethodShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                import java.util.Date;
                class A {
                    public int m() {
                        Date d;
                        String s;
                        d = new Date();
                        s = d.toGobbledygook();
                        return 0;
                    }
                }""");

        var errors = errorReports(semantics);
        assertTrue("Expected at least one semantic error", !errors.isEmpty());
        assertTrue("Expected method-not-found message",
                errors.stream().anyMatch(report -> report.getMessage().contains("not found")));
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
