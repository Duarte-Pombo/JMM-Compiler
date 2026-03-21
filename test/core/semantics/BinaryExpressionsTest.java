package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class BinaryExpressionsTest extends JmmTestEnv {

    public BinaryExpressionsTest() {
        super("", "");
    }

    @Test
    public void testArithmeticOk() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        int a;
                        int b;
                        a = 5;
                        b = 3;
                        a = a + b;
                        return a;
                    }
                }""");

        assertEquals("aa", 0, binaryExprErrors(semantics).size());
    }
    @Test
    public void testLogicalOk() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                     boolean method() {
                         boolean a;
                         boolean b;
                         a = true;
                         b = false;
                         return a && b;
                     }
                }
                """);

        assertEquals("aa",0, binaryExprErrors(semantics).size());
    }

    @Test
    public void testLogicalOrOk() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                     boolean method() {
                         boolean a;
                         boolean b;
                         a = true;
                         b = false;
                         return a || b;
                     }
                }
                """);

        assertEquals("aa",0, binaryExprErrors(semantics).size());
    }

    @Test
    public void testComparisonOk() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        int a;
                        int b;
                        a = 5;
                        b = 10;
                        return a < b;
                    }
                }""");

        assertEquals("aa",0, binaryExprErrors(semantics).size());
    }

    @Test
    public void testGreaterThanOk() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        int a;
                        int b;
                        a = 10;
                        b = 5;
                        return a > b;
                    }
                }""");

        assertEquals("aa",0, binaryExprErrors(semantics).size());
    }

    @Test
    public void testGreaterEqualOk() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        int a;
                        int b;
                        a = 10;
                        b = 10;
                        return a >= b;
                    }
                }""");

        assertEquals("aa",0, binaryExprErrors(semantics).size());
    }

    @Test
    public void testLessEqualOk() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        int a;
                        int b;
                        a = 5;
                        b = 10;
                        return a <= b;
                    }
                }""");

        assertEquals("aa",0, binaryExprErrors(semantics).size());
    }

    @Test
    public void testEqualityObjectsOk() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        NullPointerException e1;
                        Exception e2;

                        e1 = new NullPointerException();
                        e2 = new Exception();

                        if (e1 == e2) {
                            return 1;
                        }

                        return 0;
                    }
                }""");

        assertEquals("a",0, binaryExprErrors(semantics).size());
    }

    @Test
    public void testInequalityObjectsOk() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        NullPointerException e1;
                        Exception e2;

                        e1 = new NullPointerException();
                        e2 = new Exception();

                        if (e1 != e2) {
                            return 1;
                        }

                        return 0;
                    }
                }""");

        assertEquals("a",0, binaryExprErrors(semantics).size());
    }

    @Test
    public void testArithmeticInvalid() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        int a;
                        boolean b;
                        a = 5;
                        b = true;
                        a = a + b;
                        return a;
                    }
                }""");

        assertEquals("A",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testLogicalInvalid() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        int a;
                        boolean b;
                        a = 5;
                        b = true;
                        return a && b;
                    }
                }""");

        assertEquals("a",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testLogicalOrInvalid() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        int a;
                        boolean b;
                        a = 5;
                        b = true;
                        return a || b;
                    }
                }""");

        assertEquals("a",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testComparisonInvalid() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        boolean a;
                        boolean b;
                        a = true;
                        b = false;
                        return a < b;
                    }
                }""");

        assertEquals("a",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testGreaterThanInvalid() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        boolean a;
                        boolean b;
                        a = true;
                        b = false;
                        return a > b;
                    }
                }""");

        assertEquals("a",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testGreaterEqualInvalid() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        boolean a;
                        boolean b;
                        a = true;
                        b = false;
                        return a >= b;
                    }
                }""");

        assertEquals("a",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testLessEqualInvalid() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        boolean a;
                        boolean b;
                        a = true;
                        b = false;
                        return a <= b;
                    }
                }""");

        assertEquals("a",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testEqualityInvalidDifferentTypes() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        int a;
                        boolean b;
                        a = 5;
                        b = true;

                        if (a == b) {
                            return 1;
                        }

                        return 0;
                    }
                }""");

        assertEquals("a",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testInequalityInvalidDifferentTypes() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        int a;
                        boolean b;
                        a = 5;
                        b = true;

                        if (a != b) {
                            return 1;
                        }

                        return 0;
                    }
                }""");

        assertEquals("a",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testArrayArithmeticFail() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        int[] a;
                        int[] b;

                        a = new int[10];
                        b = new int[10];
                        a = a + b;

                        return 0;
                    }
                }""");

        assertEquals("a",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testArrayLogicalFail() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    boolean method() {
                        int[] a;
                        boolean b;

                        a = new int[10];
                        b = true;

                        return a && b;
                    }
                }""");

        assertEquals("a",1, binaryExprErrors(semantics).size());
    }

    @Test
    public void testArrayEquality() {
        var semantics = analyzeSnippet("""
                package x;
                class Test {
                    int method() {
                        int[] a;
                        int[] b;
                        a = new int[10];
                        b = new int[10];
                
                        if (a == b) {
                            return 1;
                        }
               
                        return 0;
                    }
                }""");

        assertEquals("a",0, binaryExprErrors(semantics).size());
    }


    private JmmSemanticsResult analyzeSnippet(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(symbolTableResult);
    }

    private List<Report> binaryExprErrors(JmmSemanticsResult semantics) {
        return semantics.getReports(ReportType.ERROR).stream()
                .filter(r -> r.getMessage().contains("Invalid binary expression"))
                .toList();
    }
}
