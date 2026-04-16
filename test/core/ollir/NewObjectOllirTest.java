package core.ollir;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class NewObjectOllirTest extends JmmTestEnv {

    public NewObjectOllirTest() {
        super("", "");
    }

    @Test
    public void newObjectShouldGenerateAllocationAndConstructor() {
        var ollirCode = toOllirCode("""
        package x;
        class A {
            public void method() {
                new A();
            }
        }""");

        assertTrue("aa",ollirCode.contains("new(A).A"));
        assertTrue("aa",ollirCode.contains("invokespecial"));
    }

    @Test
    public void newObjectUsedAsReceiver() {
        var ollirCode = toOllirCode("""
        package x;
        class A {
            public void foo() {}

            public void method() {
                new A().foo();
            }
        }""");

        assertTrue("aa",ollirCode.contains("invokespecial"));
        assertTrue("aa",ollirCode.contains("invokevirtual"));
    }

    @Test
    public void newObjectShouldGenerateTempAndConstructor() {
        var ollirCode = toOllirCode("""
        package x;
        class A {
            public void method() {
                new A();
            }
        }""");

        assertTrue("aa",ollirCode.contains("new(A).A"));
        assertTrue("aa",ollirCode.contains("invokespecial"));
        assertTrue("aa",ollirCode.contains(":=.A new(A).A"));
    }

    @Test
    public void newObjectMustAlwaysCallConstructor() {
        var ollirCode = toOllirCode("""
        package x;
        class A {
            public void method() {
                new A();
            }
        }""");

        long ctorCalls = ollirCode.lines()
                .filter(l -> l.contains("invokespecial"))
                .count();

        assertTrue("Constructor must be called at least once", ctorCalls >= 1);
    }

    @Test
    public void newObjectInReturnShouldUseTemp() {
        var ollirCode = toOllirCode("""
        package x;
        class A {
            public A method() {
                return new A();
            }
        }""");

        assertTrue("aa",ollirCode.contains(":=.A new(A).A"));
        assertTrue("aa",ollirCode.contains("ret.A"));

        assertTrue("Should not return raw new expression",
                !ollirCode.contains("ret.A new(A)"));
    }

    @Test
    public void newObjectInAssignmentShouldWork() {
        var ollirCode = toOllirCode("""
        package x;
        class A {
            public void method() {
                A a;
                a = new A();
            }
        }""");

        assertTrue("aa",ollirCode.contains("a.A :="));
        assertTrue("aa",ollirCode.contains("new(A).A"));
    }

    @Test
    public void newObjectUsedAsReceiverShouldWork() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public void foo() {}

            public void method() {
                new A().foo();
            }
        }""");

        assertTrue("a",ollirCode.contains("invokespecial"));
        assertTrue("a",ollirCode.contains("invokevirtual("));
    }

    @Test
    public void nestedNewObjectsShouldWork() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public A foo() {
                return new A();
            }

            public A method() {
                return new A().foo();
            }
        }""");

        long newCount = ollirCode.lines()
                .filter(l -> l.contains("new(A).A"))
                .count();

        assertTrue("Should create at least one object", newCount >= 1);
    }

    @Test
    public void multipleNewObjectsShouldBeIndependent() {
        var ollirCode = toOllirCode("""
        package x;
        class A {

            public void method() {
                new A();
                new A();
            }
        }""");
        assertTrue("a",ollirCode.contains("tmp0.A :=.A new(A).A"));
        assertTrue("a",ollirCode.contains("invokespecial(tmp0.A, \"<init>\").V"));

        assertTrue("a",ollirCode.contains("tmp1.A :=.A new(A).A"));
        assertTrue("a",ollirCode.contains("invokespecial(tmp1.A, \"<init>\").V"));
    }

    @Test
    public void constructorMustUseAllocatedTemp() {
        var ollirCode = toOllirCode("""
        package x;
        class A {
            public void method() {
                new A();
            }
        }""");

        assertTrue("Must allocate object to temp",
                ollirCode.contains(":=.A new(A).A"));

        assertTrue("Constructor must be called on allocated object",
                ollirCode.contains("invokespecial(tmp0.A, \"<init>\").V"));
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
