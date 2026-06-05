package core.jasmin;

import org.junit.Test;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.backend.JasminBackendImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class CallsFromJmmTest extends JmmTestEnv {

    public CallsFromJmmTest() {
        super("", "");
    }

    @Test
    public void staticMethodCall() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public static int add(int a, int b) {
                        return a + b;
                    }

                    public static void main(String[] args) {
                        int x;
                        x = Test.add(2, 3);
                    }
                }
                """);

        assertTrue("", jasminCode.contains("invokestatic test/Test/add(II)I"));
    }

    @Test
    public void instanceMethodCall() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public int inc(int x) {
                        return x + 1;
                    }

                    public static void main(String[] args) {
                        Test t;
                        int x;

                        t = new Test();
                        x = t.inc(5);
                    }
                }
                """);

        assertTrue("", jasminCode.contains("invokevirtual test/Test/inc(I)I"));
    }

    @Test
    public void newWithoutArguments() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public static void main(String[] args) {
                        Test t;
                        t = new Test();
                    }
                }
                """);

        assertTrue("", jasminCode.contains("new test/Test"));
        assertTrue("", jasminCode.contains("invokespecial test/Test/<init>()V"));
    }

    @Test
    public void constructorCallWithImportedClassArguments() {
        var jasminCode = toJasminCode("""
                package test;

                import java.util.Date;

                class Test {
                    public Date method(int year, int month, int day) {
                        Date d;
                        d = new Date(year, month, day);
                        return d;
                    }
                }
                """);

        assertTrue("", jasminCode.contains("new java/util/Date"));
        assertTrue("", jasminCode.contains("invokespecial java/util/Date/<init>(III)V"));
    }

    @Test
    public void constructorCallWithImplicitJavaLangArgument() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public String method(String original) {
                        String s;
                        s = new String(original);
                        return s;
                    }
                }
                """);

        assertTrue("", jasminCode.contains("new java/lang/String"));
        assertTrue("", jasminCode.contains("invokespecial java/lang/String/<init>(Ljava/lang/String;)V"));
    }

    @Test
    public void implicitThisCall() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public int foo() {
                        return 10;
                    }

                    public int bar() {
                        return foo();
                    }

                    public static void main(String[] args) {}
                }
                """);

        assertTrue("", jasminCode.contains("invokevirtual test/Test/foo()I"));
    }

    @Test
    public void explicitThisCall() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public int square(int x) {
                        return x * x;
                    }

                    public int compute() {
                        return this.square(4);
                    }

                    public static void main(String[] args) {}
                }
                """);

        assertTrue("", jasminCode.contains("invokevirtual test/Test/square(I)I"));
    }

    @Test
    public void chainedCallFromNewObject() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public Test getSelf() {
                        return this;
                    }

                    public int value() {
                        return 42;
                    }

                    public static void main(String[] args) {
                        int x;
                        x = new Test().getSelf().value();
                    }
                }
                """);

        assertTrue("", jasminCode.contains("new test/Test"));
        assertTrue("", jasminCode.contains("invokevirtual test/Test/getSelf()Ltest/Test;"));
        assertTrue("", jasminCode.contains("invokevirtual test/Test/value()I"));
    }

    @Test
    public void callInsideExpression() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public static int foo() {
                        return 2;
                    }

                    public static void main(String[] args) {
                        int x;
                        x = Test.foo() + 3;
                    }
                }
                """);

        assertTrue("", jasminCode.contains("invokestatic test/Test/foo()I"));
        assertTrue("", jasminCode.contains("iadd"));
    }

    @Test
    public void callAsArgument() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public static int foo() {
                        return 5;
                    }

                    public static int bar(int x) {
                        return x;
                    }

                    public static void main(String[] args) {
                        int x;
                        x = Test.bar(Test.foo());
                    }
                }
                """);

        assertTrue("", jasminCode.contains("invokestatic test/Test/foo()I"));
        assertTrue("", jasminCode.contains("invokestatic test/Test/bar(I)I"));
    }

    @Test
    public void instanceCallWithMultipleArguments() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public int sum(int a, int b) {
                        return a + b;
                    }

                    public static void main(String[] args) {
                        Test t;
                        int x;

                        t = new Test();
                        x = t.sum(10, 20);
                    }
                }
                """);

        assertTrue("", jasminCode.contains("invokevirtual test/Test/sum(II)I"));
    }

    @Test
    public void overloadedMethodCall() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public int foo(int a) {
                        return a;
                    }

                    public int foo(int a, int b) {
                        return a + b;
                    }

                    public static void main(String[] args) {
                        Test t;
                        int x;

                        t = new Test();
                        x = t.foo(5, 10);
                    }
                }
                """);

        assertTrue("", jasminCode.contains("invokevirtual test/Test/foo(II)I"));
        assertFalse("", jasminCode.contains("invokevirtual test/Test/foo(I)I"));
    }

    @Test
    public void objectArgumentCall() {
        var jasminCode = toJasminCode("""
                package test;

                class Test {
                    public int id() {
                        return 42;
                    }

                    public int consume(Test t) {
                        return t.id();
                    }

                    public static void main(String[] args) {
                        Test t1;
                        Test t2;

                        t1 = new Test();
                        t2 = new Test();

                        t1.consume(t2);
                    }
                }
                """);

        assertTrue("", jasminCode.contains("invokevirtual test/Test/consume(Ltest/Test;)I"));
        assertTrue("", jasminCode.contains("invokevirtual test/Test/id()I"));
    }

    private String toJasminCode(String code) {
        var parserResult = parseSnippet(code);
        assertTrue("Unexpected parser errors", parserResult.getReports(ReportType.ERROR).isEmpty());

        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);
        assertTrue("Unexpected semantic errors", semantics.getReports(ReportType.ERROR).isEmpty());

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);
        assertNotNull("Ollir code should not be null", ollirResult.getOllirCode());

        var jasminBackend = new JasminBackendImpl();
        JasminResult jasminResult = jasminBackend.toJasmin(ollirResult);
        assertNotNull("Jasmin result should not be null", jasminResult);
        return jasminResult.getJasminCode();
    }
}
