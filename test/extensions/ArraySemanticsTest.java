package extensions;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;


public class ArraySemanticsTest extends JmmTestEnv {

    public ArraySemanticsTest() {
        super("", "");
    }

    // --- ARRAY ACCESS (Reading) ---

    @Test
    public void validArrayAccessShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int[] a;
                        a = new int[5];
                        return a[2];
                    }
                }""");

        assertEquals("Expected 0 errors", 0, semantics.getReports(ReportType.ERROR).size());
    }

    @Test
    public void arrayAccessOnNonArrayShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int a;
                        a = 10;
                        return a[0];
                    }
                }""");

        var errors = semantics.getReports(ReportType.ERROR);
        assertEquals("Expected 1 error", 1, errors.size());
        assertTrue("Expected non-array message", errors.get(0).getMessage().contains("non-array"));
    }

    @Test
    public void arrayAccessWithNonIntIndexShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int[] a;
                        a = new int[5];
                        return a[true];
                    }
                }""");

        var errors = semantics.getReports(ReportType.ERROR);
        assertEquals("Expected 1 error", 1, errors.size());
        assertTrue("Expected index integer message", errors.get(0).getMessage().contains("index"));
    }

    // --- ARRAY ASSIGNMENT (Writing) ---

    @Test
    public void validArrayAssignmentShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int[] a;
                        a = new int[5];
                        a[1 + 1] = 10;
                        return 0;
                    }
                }""");

        assertEquals("Expected 0 errors", 0, semantics.getReports(ReportType.ERROR).size());
    }

    @Test
    public void arrayAssignmentOnNonArrayShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int a;
                        a = 10;
                        a[0] = 5;
                        return 0;
                    }
                }""");

        var errors = semantics.getReports(ReportType.ERROR);
        assertEquals("Expected 1 error", 1, errors.size());
        assertTrue("Expected non-array message", errors.get(0).getMessage().contains("not an array"));
    }

    @Test
    public void arrayAssignmentWithNonIntIndexShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int[] a;
                        a = new int[5];
                        a[false] = 10;
                        return 0;
                    }
                }""");

        var errors = semantics.getReports(ReportType.ERROR);
        assertEquals("Expected 1 error", 1, errors.size());
        assertTrue("Expected index integer message", errors.get(0).getMessage().contains("index"));
    }

    @Test
    public void arrayAssignmentWithWrongValueTypeShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int[] a;
                        a = new int[5];
                        a[0] = true;
                        return 0;
                    }
                }""");

        var errors = semantics.getReports(ReportType.ERROR);
        assertEquals("Expected 1 error", 1, errors.size());
        assertTrue("Expected value type mismatch message", errors.get(0).getMessage().contains("Cannot assign"));
    }

    @Test
    public void nestedMultidimensionalInitializerShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        int[][] a;
                        a = new int[][] {{1, 2}, {3, 4}};
                        return a[1][1];
                    }
                }""");

        assertEquals("Expected 0 errors", 0, semantics.getReports(ReportType.ERROR).size());
    }

    private JmmSemanticsResult analyzeSnippet(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(symbolTableResult);
    }
}
