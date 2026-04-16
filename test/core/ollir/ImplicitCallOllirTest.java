package core.ollir;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class ImplicitCallOllirTest extends JmmTestEnv
{
    public ImplicitCallOllirTest() {
        super("", "");
    }

    @Test
    public void implicitCallShouldUseInvokevirtualOnThis() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public int foo(int x) {
                return x;
            }

            public int method() {
                return foo(5);
            }
        }""");

        assertTrue("Should generate exact invokevirtual with this",
                ollirCode.contains("invokevirtual(this.A, \"foo\", 5.i32).i32"));
        assertTrue("Should not use invokestatic",
                !ollirCode.contains("invokestatic"));
    }

    @Test
    public void voidImplicitCallShouldGenerateStatement() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public void foo(int x) {}

            public void method() {
                foo(5);
            }
        }""");

        assertTrue("Void call must end with ;",
                ollirCode.contains("invokevirtual(this.A, \"foo\", 5.i32).V;"));

        assertTrue("Void call should not assign to temp",
                !ollirCode.contains(":=.V"));
    }

    @Test
    public void implicitCallInAssignmentShouldWork() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public int foo(int x) {
                return x;
            }

            public int method() {
                int a;
                a = foo(5);
                return a;
            }
        }""");

        assertTrue("Call must produce a value",
                ollirCode.contains("invokevirtual(this.A, \"foo\", 5.i32).i32"));

        assertTrue("Result should be assigned to variable",
                ollirCode.contains("a.i32 :="));
    }

    @Test
    public void nestedImplicitCallsShouldWork() {
        var ollirCode = toOllirCode("""
            package x;
            class A {
    
                public int foo(int x) {
                    return x;
                }
    
                public int method() {
                    return foo(foo(2));
                }
            }""");

        long count = ollirCode.lines()
                .filter(l -> l.contains("invokevirtual(this.A, \"foo\""))
                .count();
        long res = 2;
        assertEquals("Should have exactly 2 calls to foo", res, count);
    }

    @Test
    public void staticMethodImplicitCallShouldNotUseInvokestatic() {
        var ollirCode = toOllirCode("""
            package x;
            class A {
    
                public static int foo(int x) {
                    return x;
                }
    
                public int method() {
                    return foo(5);
                }
            }""");

        assertTrue("Implicit call should NOT use invokestatic even if method is static",
                !ollirCode.contains("invokestatic"));

        assertTrue("Should still use invokevirtual",
                ollirCode.contains("invokevirtual(this.A, \"foo\", 5.i32)"));
    }

    @Test
    public void implicitCallShouldNotConfuseVariableWithClass() {
        var ollirCode = toOllirCode("""
            package x;
            class A {
    
                public static void ping(int x) {}
    
                public void method() {
                    int A;
                    A = 5;
                    ping(A);
                }
            }""");

        assertTrue("Must call with this and variable argument",
                ollirCode.contains("invokevirtual(this.A, \"ping\", A.i32).V;"));

        assertTrue("Must NOT treat variable as class",
                !ollirCode.contains("invokestatic(A, \"ping\""));

        assertTrue("Must NOT use class as receiver in virtual call",
                !ollirCode.contains("invokevirtual(A, \"ping\""));
    }

    @Test
    public void implicitCallWithMultipleArgsShouldWork() {
        var ollirCode = toOllirCode("""
            package x;
            class A {
    
                public int sum(int a, int b) {
                    return a + b;
                }
    
                public int method() {
                    return sum(1, 2);
                }
            }""");
        assertTrue("Should include both arguments correctly",
                ollirCode.contains("invokevirtual(this.A, \"sum\", 1.i32, 2.i32).i32"));
    }

    private String toOllirCode(String code) {
        var parserResult = parseSnippet(code);
        assertTrue("Unexpected parser errors", parserResult.getReports(ReportType.ERROR).isEmpty());

        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);
        assertTrue("Unexpected semantic errors", semantics.getReports(ReportType.ERROR).isEmpty());

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);
        assertNotNull("Ollir code should not be null", ollirResult.getOllirCode());
        return ollirResult.getOllirCode();
    }
}
