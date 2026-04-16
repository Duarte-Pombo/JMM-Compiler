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
        assertTrue("Call on object should use invokevirtual",
                ollirCode.contains("invokevirtual(a.A, \"foo\", 3.i32).i32"));
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

        assertTrue("Should not incorrectly use invokestatic with variable as class",
                !ollirCode.contains("invokestatic(A, \"ping\""));
    }

    @Test
    public void mixedStaticAndVirtualCallsShouldWork() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public int foo(int x) {
                return x;
            }

            public static int inc(int x) {
                return x + 1;
            }

            public int method() {
                return A.inc(this.foo(2));
            }
        }""");

        assertTrue("Should contain inner virtual call",
                ollirCode.contains("invokevirtual(this.A, \"foo\", 2.i32)"));

        assertTrue("Should contain outer static call",
                ollirCode.contains("invokestatic(A, \"inc\""));
    }

    @Test
    public void callAsArgumentOfVoidMethodShouldWork() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public int foo(int x) {
                return x;
            }

            public void print(int x) {}

            public void method() {
                print(this.foo(3));
            }
        }""");

        assertTrue("Inner call must exist",
                ollirCode.contains("invokevirtual(this.A, \"foo\", 3.i32)"));

        assertTrue("Outer call must be invokevirtual to print",
                ollirCode.contains("invokevirtual(this.A, \"print\""));
    }

    @Test
    public void deepNestedCallsShouldWork() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public int f(int x) {
                return x;
            }

            public int method() {
                return this.f(this.f(this.f(1)));
            }
        }""");

        long calls = ollirCode.lines()
                .filter(l -> l.contains("invokevirtual"))
                .count();

        assertTrue("Should generate multiple virtual calls", calls >= 3);
    }

    @Test
    public void mixedStaticAndInstanceCallsInSameMethod() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public static int inc(int x) {
                return x + 1;
            }

            public int foo(int x) {
                return x;
            }

            public int method() {
                int a;
                a = A.inc(1);
                a = this.foo(a);
                return a;
            }
        }""");

        assertTrue("Should contain static call",
                ollirCode.contains("invokestatic(A, \"inc\", 1.i32)"));

        assertTrue("Should contain virtual call",
                ollirCode.contains("invokevirtual(this.A, \"foo\""));
    }

    @Test
    public void newObjectCallWithoutArgsShouldWork() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public void foo() {}

            public void method() {
                new A().foo();
            }
        }""");

        assertTrue("a",ollirCode.contains("new(A).A"));
        assertTrue("a",ollirCode.contains("invokespecial") || ollirCode.contains("invokevirtual"));
        assertTrue("a",ollirCode.contains("\"foo\""));
    }

    @Test
    public void newObjectCallInReturnShouldWork() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public int foo() {
                return 42;
            }

            public int method() {
                return new A().foo();
            }
        }""");

        assertTrue("a",ollirCode.contains("invokevirtual"));
        assertTrue("a",ollirCode.contains("ret.i32"));
        assertTrue("a",ollirCode.contains("new(A).A"));
    }

    @Test
    public void newObjectCallAssignedShouldWork() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public int foo() {
                return 1;
            }

            public int method() {
                A a;
                a = new A();
                return a.foo();
            }
        }""");

        assertTrue("a",ollirCode.contains("tmp"));
        assertTrue("a",ollirCode.contains("invokevirtual(a.A, \"foo\""));
    }

    @Test
    public void newObjectUsedAsReceiverMultipleTimes() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public int foo() {
                return 1;
            }

            public int method() {
                return new A().foo();
            }
        }""");

        long newCount = ollirCode.lines()
                .filter(l -> l.contains("new(A).A"))
                .count();
        long res = 1;
        assertTrue("a",newCount >= res);
        assertTrue("a",ollirCode.contains("invokevirtual"));
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
