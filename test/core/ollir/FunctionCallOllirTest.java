package core.ollir;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class FunctionCallOllirTest extends JmmTestEnv {

    public FunctionCallOllirTest() {
        super("", "");
    }

    @Test
    public void staticVoidCallShouldGenerateInvokestaticStatement() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    public static void ping(int x) {
                    }

                    public void method() {
                        A.ping(1);
                    }
                }""");

        assertTrue("Static void call should use invokestatic", ollirCode.contains("invokestatic(A, \"ping\", 1.i32).V;"));
        assertTrue("Static call should not be emitted as invokevirtual", !ollirCode.contains("invokevirtual(A, \"ping\""));
    }

    @Test
    public void staticCallWithReturnShouldGenerateInvokestaticWithI32Return() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    public static int sum(int a, int b) {
                        return a + b;
                    }

                    public int method() {
                        return A.sum(1, 2);
                    }
                }""");

        assertTrue("Static call with return should use invokestatic", ollirCode.contains("invokestatic(A, \"sum\", 1.i32, 2.i32).i32"));
        assertTrue("Returned value should be used as int", ollirCode.contains("ret.i32"));
    }

    @Test
    public void staticCallInsideExpressionShouldStillUseInvokestatic() {
        var ollirCode = toOllirCode("""
            package x;
            class A {

                public static int inc(int x) {
                    return x + 1;
                }

                public int method() {
                    int a;
                    a = A.inc(5);
                    return a;
                }
            }""");

        assertTrue("Static call in assignment should use invokestatic",
                ollirCode.contains("invokestatic(A, \"inc\", 5.i32).i32"));
    }


    @Test
    public void nestedCallsShouldGenerateMultipleInvokes() {
        var ollirCode = toOllirCode("""
            package x;
            class A {

                public static int inc(int x) {
                    return x + 1;
                }

                public static int doubleIt(int x) {
                    return x * 2;
                }

                public int method() {
                    return A.doubleIt(A.inc(5));
                }
            }""");

        assertTrue("Should contain inner invokestatic",
                ollirCode.contains("invokestatic(A, \"inc\", 5.i32).i32"));

        assertTrue("Should contain outer invokestatic",
                ollirCode.contains("invokestatic(A, \"doubleIt\""));
    }

    @Test
    public void virtualCallOnThisShouldGenerateInvokevirtual() {
        var ollirCode = toOllirCode("""
            package x;
            class A {

                public int foo(int x) {
                    return x;
                }

                public int method() {
                    return this.foo(5);
                }
            }""");
        assertTrue("Call on this should use invokevirtual",
                ollirCode.contains("invokevirtual(this.A, \"foo\", 5.i32).i32"));

        assertTrue("Should not use invokestatic",
                !ollirCode.contains("invokestatic(this.A, \"foo\""));
    }

    @Test
    public void virtualCallOnObjectShouldGenerateInvokevirtual() {
        var ollirCode = toOllirCode("""
            package x;
            class A {

                public int foo(int x) {
                    return x;
                }

                public int method() {
                    A a;
                    a = new A();
                    return a.foo(3);
                }
            }""");
        System.out.println(ollirCode);
        assertTrue("Call on object should use invokevirtual",
                ollirCode.contains("invokevirtual(a.A, \"foo\", 3.i32).i32"));
    }

    @Test
    public void voidVirtualCallShouldStillGenerateInvokevirtualStatement() {
        var ollirCode = toOllirCode("""
            package x;
            class A {

                public void foo(int x) {it
                }

                public void method() {
                    this.foo(10);
                }
            }""");
        assertTrue("Void virtual call should use invokevirtual",
                ollirCode.contains("invokevirtual(this.A, \"foo\", 10.i32).V;"));
    }

    @Test
    public void shouldNotConfuseClassWithVariableName() {
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

        System.out.println(ollirCode);
        assertTrue("Should not incorrectly use invokestatic with variable as class",
                !ollirCode.contains("invokestatic(A, \"ping\""));
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
