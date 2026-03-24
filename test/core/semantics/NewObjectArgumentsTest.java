package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class NewObjectArgumentsTest extends JmmTestEnv {

    public NewObjectArgumentsTest() {
        super("", "");
    }

    @Test
    public void currentClassWithArgumentsShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public A make() {
                        return new A(1);
                    }
                }""");

        var errors = errorReports(semantics);
        assertTrue("Expected at least one semantic error", !errors.isEmpty());
        assertTrue("Expected constructor-not-found message",
                errors.stream().anyMatch(report -> report.getMessage().contains("Constructor 'A(int)' not found")));
    }

    @Test
    public void implicitImportedConstructorWithMatchingArgsShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public Exception make() {
                        return new Exception(new Exception());
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void implicitImportedConstructorWithWrongArgTypeShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public Exception make() {
                        return new Exception(1);
                    }
                }""");

        var errors = errorReports(semantics);
        assertTrue("Expected at least one semantic error", !errors.isEmpty());
        assertTrue("Expected constructor-not-found message",
                errors.stream().anyMatch(report -> report.getMessage().contains("Constructor 'Exception(int)' not found")));
    }

    @Test
    public void importedConstructorWithMatchingArgsShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                import java.util.Date;
                class A {
                    public Date make() {
                        return new Date(new String());
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void importedConstructorWithWrongArgTypeShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                import java.util.Date;
                class A {
                    public Date make() {
                        return new Date(new Exception());
                    }
                }""");

        var errors = errorReports(semantics);
        assertTrue("Expected at least one semantic error", !errors.isEmpty());
        assertTrue("Expected constructor-not-found message",
                errors.stream().anyMatch(report ->
                        report.getMessage().contains("Constructor 'Date(")
                                && report.getMessage().contains("not found")));
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
