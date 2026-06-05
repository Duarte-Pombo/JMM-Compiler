package core.jasmin;

import org.junit.Test;
import org.specs.comp.ollir.Ollir;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.backend.JasminBackendImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

import java.util.List;
import java.util.Map;

public class CallsTest extends JmmTestEnv {

    public CallsTest() {
        super("", "");
    }

    @Test
    public void invokeStaticMethod() {
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

        assertTrue("",jasminCode.contains("invokestatic test/Test/add(II)I"));
    }

    @Test
    public void invokeInstanceMethod() {
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

        assertTrue("",jasminCode.contains("invokevirtual test/Test/inc(I)I"));
    }

    @Test
    public void invokeNewWithoutArguments() {
        var jasminCode = toJasminCode("""
        package test;
        class Test {

            public static void main(String[] args) {
                Test t;
                t = new Test();
            }
        }
        """);
        assertTrue("",jasminCode.contains("new test/Test"));
        assertTrue("",jasminCode.contains("invokespecial test/Test/<init>()V"));
    }

    @Test
    public void localStaticMethod() {
        var jasminCode = toJasminCode("""
        package test;
        class Test {

            public static int foo() {
                return 1;
            }

            public static void main(String[] args) {
                int x;
                x = Test.foo();
            }
        }
        """);

        assertTrue("",jasminCode.contains(".method public static foo()I"));
        assertTrue("",jasminCode.contains("invokestatic test/Test/foo()I"));
    }

    @Test
    public void newWithArguments() {
        var jasminCode = toJasminCode("""
        package test;
        class Test {

            public Test init(int a, int b) {
                return this;
            }

            public static void main(String[] args) {
                Test t;
                t = new Test().init(1, 2);
            }
        }
        """);
        System.out.println(jasminCode);
        assertTrue("",jasminCode.contains("new test/Test"));
        assertTrue("",jasminCode.contains("invokevirtual test/Test/init(II)Ltest/Test;"));
    }

    @Test
    public void constructorCallWithArguments() {
        var jasminCode = toJasminCodeFromOllir("""
                package test;
                import java.util.Date;

                Test extends Object {
                    .construct "<init>"().V {
                        invokespecial(this."java.lang.Object", "<init>").V;
                    }

                    .method public method(year.i32, month.i32, day.i32).Date {
                        tmp0.Date :=.Date new(Date).Date;
                        invokespecial(tmp0.Date, "<init>", year.i32, month.i32, day.i32).V;
                        ret.Date tmp0.Date;
                    }
                }
                """);

        assertTrue("", jasminCode.contains("new java/util/Date"));
        assertTrue("", jasminCode.contains("invokespecial java/util/Date/<init>(III)V"));
    }

    @Test
    public void implicitThis() {
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

        assertTrue("",jasminCode.contains("invokevirtual test/Test/foo()I"));
    }

    @Test
    public void currentObjectMethod() {
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
        assertTrue("",jasminCode.contains("invokevirtual test/Test/square(I)I"));

    }

    @Test
    public void chainedCall() {
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

        assertTrue("",jasminCode.contains("new test/Test"));
        assertTrue("",jasminCode.contains("invokevirtual test/Test/getSelf()Ltest/Test;"));
        assertTrue("",jasminCode.contains("invokevirtual test/Test/value()I"));
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

        assertTrue("",jasminCode.contains("invokestatic test/Test/foo()I"));
        assertTrue("",jasminCode.contains("iadd"));
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

        assertTrue("",jasminCode.contains("invokestatic test/Test/foo()I"));
        assertTrue("",jasminCode.contains("invokestatic test/Test/bar(I)I"));
    }

    @Test
    public void instanceCallMultipleArgs() {
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

        assertTrue("",jasminCode.contains("invokevirtual test/Test/sum(II)I"));
    }

    @Test
    public void thisInCallChain() {
        var jasminCode = toJasminCode("""
    package test;
    class Test {

        public Test next() {
            return this;
        }

        public int id() {
            return 7;
        }

        public int run() {
            return this.next().id();
        }

        public static void main(String[] args) {}
    }
    """);

        assertTrue("",jasminCode.contains("aload_0"));
        assertTrue("",jasminCode.contains("invokevirtual test/Test/next()Ltest/Test;"));
        assertTrue("",jasminCode.contains("invokevirtual test/Test/id()I"));
    }

    @Test
    public void staticCallRepeated() {
        var jasminCode = toJasminCode("""
    package test;
    class Test {

        public static int a() {
            return 1;
        }

        public static int b() {
            return Test.a();
        }

        public static void main(String[] args) {
            int x;
            x = Test.b();
        }
    }
    """);

        assertTrue("",jasminCode.contains("invokestatic test/Test/a()I"));
        assertTrue("",jasminCode.contains("invokestatic test/Test/b()I"));
    }

    @Test
    public void methodOverloading() {
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

        assertTrue("",jasminCode.contains("invokevirtual test/Test/foo(II)I"));
        assertFalse("",jasminCode.contains("invokevirtual test/Test/foo(I)I"));
    }

    @Test
    public void callWithObjectArgument() {
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

        assertTrue("",jasminCode.contains("invokevirtual test/Test/consume(Ltest/Test;)I"));
        assertTrue("",jasminCode.contains("invokevirtual test/Test/id()I"));
    }

    @Test
    public void bytecodeOrderCallEvaluation() {
        var jasminCode = toJasminCode("""
    package test;
    class Test {

        public static int foo() {
            return 10;
        }

        public static int bar() {
            return Test.foo();
        }

        public static void main(String[] args) {
            int x;
            x = Test.bar();
        }
    }
    """);

        String code = jasminCode.replace("\r", "");
        String[] lines = code.split("\n");

        int fooIndex = -1;
        int barIndex = -1;

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("invokestatic test/Test/foo()I")) {
                fooIndex = i;
            }
            if (lines[i].contains("invokestatic test/Test/bar()I")) {
                barIndex = i;
            }
        }

        assertTrue("",fooIndex != -1);
        assertTrue("",barIndex != -1);

        assertTrue("",fooIndex < barIndex);
    }

    @Test
    public void callAndArithmeticOrder() {
        var jasminCode = toJasminCode("""
    package test;
    class Test {

        public static int foo() {
            return 2;
        }

        public static void main(String[] args) {
            int x;
            x = Test.foo() * 3 + 1;
        }
    }
    """);

        String code = jasminCode.replace("\r", "");
        String[] lines = code.split("\n");

        int fooIndex = -1;
        int imulIndex = -1;
        int iaddIndex = -1;

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("invokestatic test/Test/foo()I")) fooIndex = i;
            if (lines[i].contains("imul")) imulIndex = i;
            if (lines[i].contains("iadd")) iaddIndex = i;
        }

        assertTrue("",fooIndex != -1);
        assertTrue("",imulIndex != -1);
        assertTrue("",iaddIndex != -1);

        assertTrue("",fooIndex < imulIndex);
        assertTrue("",imulIndex < iaddIndex);
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

    private String toJasminCodeFromOllir(String ollirCode) {
        var parseResult = Ollir.parse(ollirCode);
        assertFalse("OLLIR parse should not have errors", parseResult.hasErrors());

        var classUnit = parseResult.classUnit();
        classUnit.buildVarTables();

        var ollirResult = new OllirResult(classUnit, List.<Report>of(), Map.of());
        var jasminBackend = new JasminBackendImpl();
        JasminResult jasminResult = jasminBackend.toJasmin(ollirResult);
        assertNotNull("Jasmin result should not be null", jasminResult);
        return jasminResult.getJasminCode();
    }
}
