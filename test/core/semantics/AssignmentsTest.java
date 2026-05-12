package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class AssignmentsTest extends JmmTestEnv {

    public AssignmentsTest() {
        super("", "");
    }

    @Test
    public void intAssignmentShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        int a;
                        int b;
                        a = b;
                    }
                }""");

        assertEquals("aaa",0, assignmentErrors(semantics).size());
    }
    @Test
    public void intBooleanAssignmentShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        int a;
                        boolean b;
                        a = b;
                    }
                }""");

        var errors = assignmentErrors(semantics);
        assertEquals("aaa",1, errors.size());
        assertTrue("aaa",errors.get(0).getMessage().contains("Cannot assign expression of type"));
    }

    @Test
    public void booleanAssignmentShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        boolean a;
                        boolean b;
                        a = b;
                    }
                }""");

        assertEquals("aaa",0, assignmentErrors(semantics).size());
    }

    @Test
    public void expressionAssignmentShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        int a;
                        int b;
                        a = b + 1;
                    }
                }""");

        assertEquals("aaa",0, assignmentErrors(semantics).size());
    }

    @Test
    public void exceptionSubclassShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        Exception e;
                        e = new NullPointerException();
                        return 0;
                    }
                }""");

        assertEquals("aa",0, assignmentErrors(semantics).size());
    }

    @Test
    public void errorNotExceptionShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        Exception e;
                        e = new AssertionError();
                        return 0;
                    }
                }""");

        var errors = assignmentErrors(semantics);
        assertEquals("a",1, errors.size());
    }

    @Test
    public void throwableShouldAcceptExceptionAndError() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        Throwable t;
                        t = new Exception();
                        t = new AssertionError();
                        return 0;
                    }
                }""");

        assertEquals("aa",0, assignmentErrors(semantics).size());
    }

    @Test
    public void exceptionShouldNotAcceptError() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        Exception e;
                        e = new Error();
                        return 0;
                    }
                }""");

        var errors = assignmentErrors(semantics);
        assertEquals("a",1, errors.size());
    }

    @Test
    public void objectShouldAcceptEverything() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        Object o;
                        o = new String();
                        o = new Exception();
                        o = new AssertionError();
                        return 0;
                    }
                }""");

        assertEquals("a",0, assignmentErrors(semantics).size());
    }

    @Test
    public void stringToObjectShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        Object o;
                        o = new String();
                        return 0;
                    }
                }""");

        assertEquals("a",0, assignmentErrors(semantics).size());
    }

    @Test
    public void objectToStringShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        Object o;
                        String s;
                        s = o;
                        return 0;
                    }
                }""");

        var errors = assignmentErrors(semantics);
        assertEquals("aaa",1, errors.size());
    }

    @Test
    public void invalidForInitAssignmentShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m(boolean value) {
                        int res;
                        for(res = value; 0 < res; res = res - 1) {
                        }
                        return res;
                    }
                }""");

        var errors = assignmentErrors(semantics);
        assertEquals("aaa",1, errors.size());
    }

    @Test
    public void invalidForUpdateAssignmentShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m(boolean value) {
                        int res;
                        res = 5;
                        for(; 0 < res; res = value) {
                        }
                        return res;
                    }
                }""");

        var errors = assignmentErrors(semantics);
        assertEquals("aaa",1, errors.size());
    }

    @Test
    public void binaryExpressionAsAssigneeShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        int a;
                        int b;
                        (a + b) = 1;
                    }
                }""");

        assertTrue("Expected semantic error for non-assignable LHS", semantics.getReports(ReportType.ERROR).size() > 0);
    }

    @Test
    public void methodCallAsAssigneeShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int f() {
                        return 0;
                    }
                    public void m() {
                        f() = 1;
                    }
                }""");

        assertTrue("Expected semantic error for non-assignable LHS", semantics.getReports(ReportType.ERROR).size() > 0);
    }

    @Test
    public void newObjectAsAssigneeShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        A other;
                        other = new A();
                        new A() = other;
                    }
                }""");

        assertTrue("Expected semantic error for non-assignable LHS", semantics.getReports(ReportType.ERROR).size() > 0);
    }

    @Test
    public void literalAsAssigneeShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        int a;
                        1 = a;
                    }
                }""");

        assertTrue("Expected semantic error for non-assignable LHS", semantics.getReports(ReportType.ERROR).size() > 0);
    }

    @Test
    public void negationAsAssigneeShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        boolean b;
                        (!b) = false;
                    }
                }""");

        assertTrue("Expected semantic error for non-assignable LHS", semantics.getReports(ReportType.ERROR).size() > 0);
    }

    @Test
    public void arrayLengthAsAssigneeShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        int[] arr;
                        arr = new int[3];
                        arr.length = 2;
                    }
                }""");

        assertTrue("Expected semantic error for non-assignable LHS", semantics.getReports(ReportType.ERROR).size() > 0);
    }

    @Test
    public void arrayElementFromObjectFieldAsAssigneeShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int[] foo;

                    public void m() {
                        A bar;
                        bar.foo[1] = 7;
                    }
                }""");

        assertEquals("Expected no assignment errors", 0, assignmentErrors(semantics).size());
        assertEquals("Expected no semantic errors", 0, semantics.getReports(ReportType.ERROR).size());
    }

    private JmmSemanticsResult analyzeSnippet(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(symbolTableResult);
    }

    private List<Report> assignmentErrors(JmmSemanticsResult semantics) {
        return semantics.getReports(ReportType.ERROR).stream()
                .filter(r -> r.getMessage().contains("Cannot assign expression of type"))
                .toList();
    }
}
