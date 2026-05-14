package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class CallsTest extends JmmTestEnv {

    public CallsTest() {
        super("", "");
    }

    @Test
    public void implicitInheritedObjectMethodShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public String foo() {
                        return toString();
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void implicitLocalMethodShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int bar(int x) {
                        return x;
                    }

                    public int foo() {
                        return bar(3);
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void explicitInheritedObjectMethodShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public String foo() {
                        return this.toString();
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void missingImplicitMethodShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int foo() {
                        missingCall();
                        return 0;
                    }
                }""");

        var errors = errorReports(semantics);
        assertTrue("Expected at least one semantic error", !errors.isEmpty());
        assertTrue("Expected method-not-found message",
                errors.stream().anyMatch(report -> report.getMessage().contains("Method not found")));
    }

    @Test
    public void methodCallOnPrimitiveReceiverShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int foo() {
                        1.foo();
                        return 0;
                    }
                }""");

        var errors = errorReports(semantics);
        assertTrue("Expected at least one semantic error", !errors.isEmpty());
        assertTrue("Expected invalid receiver message",
                errors.stream().anyMatch(report -> report.getMessage().contains("receiver is not a class type")));
    }

    @Test
    public void testNonStaticSelfMethodCalledOnClassWithThisShouldFail() {
        setDescription("Test Java-like rejection of instance method call through class name with this argument");
        semanticsFromSnippet("""
                package x;
                class A {
                    A bar(A value) {
                        return value;
                    }

                    A foo() {
                        return A.bar(this);
                    }
                }""", true);
    }

    @Test
    public void testNonStaticSelfMethodCalledOnClassWithThisShouldPass() {
        setDescription("Test Java-like rejection of instance method call through class name with this argument");
        semanticsFromSnippet("""
                package x;
                class A {
                    A bar(A value) {
                        return value;
                    }

                    A foo() {
                        return this.bar(this);
                    }
                }""", false);
    }

    @Test
    public void testNonStaticSelfMethodCalledOnClassWithThisShouldPass2() {
        setDescription("Test Java-like rejection of instance method call through class name with this argument");
        semanticsFromSnippet("""
                package x;
                class A {
                    A bar(A value) {
                        return value;
                    }

                    A foo() {
                        return bar(this);
                    }
                }""", false);
    }

    @Test
    public void testStaticSelfMethodCalledOnClassWithThisShouldPass() {
        setDescription("Test Java-like acceptance of static call through class name with this argument");
        semanticsFromSnippet("""
                package x;
                class A {
                    static A bar(A value) {
                        return value;
                    }

                    A foo() {
                        return A.bar(this);
                    }
                }""", false);
    }

    @Test
    public void ownClassParamThisAndAliasShouldBehaveSameInChainedReturn() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    A bar(A value) {
                        return value;
                    }

                    A viaThis() {
                        return this.bar(this);
                    }

                    A viaAlias() {
                        A alias;
                        alias = this;
                        return this.bar(alias);
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void ownClassParamShouldAcceptParenthesizedThis() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    A bar(A value) {
                        return value;
                    }

                    A foo() {
                        return bar((this));
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void ownClassArrayParamShouldRejectThis() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    void use(A[] values) {
                    }

                    int foo() {
                        use(this);
                        return 0;
                    }
                }""");

        assertTrue("Expected method call error for passing 'this' to A[] parameter",
                errorReports(semantics).stream()
                        .anyMatch(report -> report.getMessage().contains("Method call failed")));
    }

    @Test
    public void implicitThisCallToInstanceMethodInStaticContextShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    A bar(A value) {
                        return value;
                    }

                    static A foo(A value) {
                        return bar(value);
                    }
                }""");

        assertTrue("Expected semantic error for implicit this call in static context",
                !errorReports(semantics).isEmpty());
    }

    @Test
    public void implicitCallToStaticMethodInStaticContextShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    static A bar(A value) {
                        return value;
                    }

                    static A foo(A value) {
                        return bar(value);
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void explicitThisCallToInstanceMethodInStaticContextShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    A bar(A value) {
                        return value;
                    }

                    static A foo(A value) {
                        return this.bar(value);
                    }
                }""");

        assertTrue("Expected semantic error for explicit this call in static context",
                !errorReports(semantics).isEmpty());
    }

    @Test
    public void explicitThisCallToStaticMethodInStaticContextShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    static A bar(A value) {
                        return value;
                    }

                    static A foo(A value) {
                        return this.bar(value);
                    }
                }""");

        assertTrue("Expected semantic error because 'this' is illegal in static context",
                !errorReports(semantics).isEmpty());
    }

    @Test
    public void classCallToInstanceMethodInStaticContextShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    A bar(A value) {
                        return value;
                    }

                    static A foo(A value) {
                        return A.bar(value);
                    }
                }""");

        assertTrue("Expected semantic error for class-qualified call to instance method",
                !errorReports(semantics).isEmpty());
    }

    @Test
    public void classCallToStaticMethodInStaticContextShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    static A bar(A value) {
                        return value;
                    }

                    static A foo(A value) {
                        return A.bar(value);
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void implicitCallToStaticMethodInInstanceContextShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    static A bar(A value) {
                        return value;
                    }

                    A foo(A value) {
                        return bar(value);
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    @Test
    public void explicitThisCallToStaticMethodInInstanceContextShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    static A bar(A value) {
                        return value;
                    }

                    A foo(A value) {
                        return this.bar(value);
                    }
                }""");

        assertEquals("Expected no semantic errors", 0, errorReports(semantics).size());
    }

    private JmmSemanticsResult analyzeSnippet(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(symbolTableResult);
    }

    private List<Report> errorReports(JmmSemanticsResult semantics) {
        return semantics.getReports(ReportType.ERROR);
    }
}
