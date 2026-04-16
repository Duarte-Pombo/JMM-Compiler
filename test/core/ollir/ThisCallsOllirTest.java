package core.ollir;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class ThisCallsOllirTest extends JmmTestEnv {

    public ThisCallsOllirTest() {
        super("", "");
    }

    @Test
    public void thisAsReceiverShouldGenerateInvokevirtual() {
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
                ollirCode.contains("invokevirtual(this"));

        assertTrue("Should call correct method",
                ollirCode.contains("\"foo\""));
    }

    @Test
    public void implicitThisCallShouldBehaveLikeThisCall() {
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

        assertTrue("Implicit call should use invokevirtual on this",
                ollirCode.contains("invokevirtual(this"));
    }

    @Test
    public void thisShouldNotBeTreatedAsStatic() {
        var ollirCode = toOllirCode("""
            package x;
            class A {

                public int foo() {
                    return 1;
                }

                public int method() {
                    return this.foo();
                }
            }""");

        assertTrue("this should not use invokestatic",
                !ollirCode.contains("invokestatic(this"));
    }

    @Test
    public void thisInsideAssignmentShouldWork() {
        var ollirCode = toOllirCode("""
            package x;
            class A {

                public int foo() {
                    return 1;
                }

                public int method() {
                    int a;
                    a = this.foo();
                    return a;
                }
            }""");

        assertTrue("this call in assignment should work",
                ollirCode.contains("invokevirtual(this"));
    }

    @Test
    public void thisShouldNotBeMistakenForClass() {
        var ollirCode = toOllirCode("""
            package x;
            class A {

                public int foo() {
                    return 1;
                }

                public int method() {
                    return this.foo();
                }
            }""");

        assertTrue("this should not be treated as class reference",
                !ollirCode.contains("invokestatic(this"));
    }

    @Test
    public void thisWithMultipleCallsShouldBeConsistent() {
        var ollirCode = toOllirCode("""
            package x;
            class A {

                public int foo(int x) {
                    return x;
                }

                public int method() {
                    return this.foo(this.foo(2));
                }
            }""");

        assertTrue("Nested calls should still use this correctly",
                ollirCode.contains("invokevirtual(this"));
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
