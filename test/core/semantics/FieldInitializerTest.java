package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class FieldInitializerTest extends JmmTestEnv {

    public FieldInitializerTest() {
        super("", "");
    }

    @Test
    public void validPrimitiveInitializersShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int a = 10;
                    boolean b = true;
                    
                    public int m() {
                        return a;
                    }
                }""");

        assertEquals("Should have 0 errors", 0, initializerErrors(semantics).size());
    }

    @Test
    public void validExpressionInitializerShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int a = 5 + 10 * 2;
                    boolean b = !false;
                }""");

        assertEquals("Should have 0 errors", 0, initializerErrors(semantics).size());
    }

    @Test
    public void validObjectInitializerShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    A self = new A();
                    int[] arr = new int[5];
                }""");

        assertEquals("Should have 0 errors", 0, initializerErrors(semantics).size());
    }

    @Test
    public void fieldInitializerReferencingAnotherFieldShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int a = 1;
                    int b = a;
                }""");

        assertEquals("Should have 0 errors", 0, initializerErrors(semantics).size());
    }

    @Test
    public void invalidIntAssignedBooleanShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int a = true;
                }""");

        var errors = initializerErrors(semantics);
        assertEquals("Should have 1 error", 1, errors.size());
    }

    @Test
    public void invalidFieldInitializerShouldProduceSemanticError() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int a = true;
                }""");

        assertTrue("Expected at least one semantic error",
                !semantics.getReports(ReportType.ERROR).isEmpty());
    }

    @Test
    public void invalidFieldInitializerShouldReportTypeMismatch() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int a = true;
                }""");

        assertTrue("Expected a type mismatch error for field initializer",
                semantics.getReports(ReportType.ERROR).stream()
                        .anyMatch(report -> report.getMessage().contains("initialize variable")
                                || report.getMessage().contains("Cannot assign")
                                || report.getMessage().contains("type")));
    }

    @Test
    public void invalidBooleanAssignedIntShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    boolean b = 10;
                }""");

        var errors = initializerErrors(semantics);
        assertEquals("Should have 1 error", 1, errors.size());
    }

    @Test
    public void invalidArrayAssignedIntShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int[] arr = 5;
                }""");

        var errors = initializerErrors(semantics);
        assertEquals("Should have 1 error", 1, errors.size());
    }

    @Test
    public void multipleInvalidInitializersShouldReportMultipleErrors() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int a = false;
                    boolean b = 1 + 1;
                }""");

        var errors = initializerErrors(semantics);
        assertEquals("Should have 2 errors", 2, errors.size());
    }

    private JmmSemanticsResult analyzeSnippet(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(symbolTableResult);
    }

    private List<Report> initializerErrors(JmmSemanticsResult semantics) {
        return semantics.getReports(ReportType.ERROR).stream()
                // Checks for either the field initialization specific message or the general assignment message
                // depending on how you merged it into Assignments.java
                .filter(r -> r.getMessage().contains("initialize variable") || r.getMessage().contains("Cannot assign"))
                .toList();
    }
}
