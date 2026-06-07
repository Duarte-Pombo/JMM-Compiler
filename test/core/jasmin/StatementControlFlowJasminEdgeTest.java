package core.jasmin;

import org.junit.Test;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JasminTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.backend.JasminBackendImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class StatementControlFlowJasminEdgeTest extends JasminTestEnv {

    public StatementControlFlowJasminEdgeTest() {
        super("", "");
    }

    @Test
    public void doWhileRunsBodyOnceWhenConditionIsInitiallyFalse() {
        var compiled = compileSnippet("""
                package x;
                class A {
                    public int method() {
                        int i;
                        i = 0;
                        do {
                            i = i + 1;
                        } while (false);
                        return i;
                    }
                }""");

        var ret = compiled.invoke("method", Integer.class);
        assertEquals("do-while body must run before checking the condition", 1, ret.returnValue());
    }

    @Test
    public void doWhileConditionUsesStateMutatedByBody() {
        var compiled = compileSnippet("""
                package x;
                class A {
                    public int method(int limit) {
                        int i;
                        i = 0;
                        do {
                            i = i + 1;
                        } while (i < limit);
                        return i;
                    }
                }""");

        assertEquals("limit 0 should still execute one iteration", 1,
                compiled.invoke("method", Integer.class, 0).returnValue());
        assertEquals("condition should see the value updated in the body", 3,
                compiled.invoke("method", Integer.class, 3).returnValue());
    }

    @Test
    public void forWithFalseInitialConditionSkipsBodyAndUpdate() {
        var compiled = compileSnippet("""
                package x;
                class A {
                    public int method() {
                        int i;
                        int marker;
                        i = 5;
                        marker = 0;
                        for (; i < 0; i = i + 1) {
                            marker = 99;
                        }
                        return i * 10 + marker;
                    }
                }""");

        var ret = compiled.invoke("method", Integer.class);
        assertEquals("false initial condition must skip both body and update", 50, ret.returnValue());
    }

    @Test
    public void forUpdateRunsAfterTheBody() {
        var compiled = compileSnippet("""
                package x;
                class A {
                    public int method() {
                        int i;
                        int sum;
                        i = 0;
                        sum = 0;
                        for (; i < 3; i = i + 1) {
                            sum = sum + i;
                        }
                        return sum * 10 + i;
                    }
                }""");

        var ret = compiled.invoke("method", Integer.class);
        assertEquals("body should observe i before the update expression runs", 33, ret.returnValue());
    }

    @Test
    public void ifWithoutElseFallsThroughToFollowingStatement() {
        var compiled = compileSnippet("""
                package x;
                class A {
                    public int method(boolean flag) {
                        int res;
                        res = 1;
                        if (flag) {
                            res = 10;
                        }
                        res = res + 2;
                        return res;
                    }
                }""");

        assertEquals("false branch should continue after the if body", 3,
                compiled.invoke("method", Integer.class, false).returnValue());
        assertEquals("true branch should execute body and then continue after it", 12,
                compiled.invoke("method", Integer.class, true).returnValue());
    }

    @Test
    public void nestedIfWithoutElseUsesIndependentExitLabels() {
        var compiled = compileSnippet("""
                package x;
                class A {
                    public int method(boolean outer, boolean inner) {
                        int res;
                        res = 0;
                        if (outer) {
                            res = res + 1;
                            if (inner) {
                                res = res + 10;
                            }
                            res = res + 100;
                        }
                        res = res + 1000;
                        return res;
                    }
                }""");

        assertEquals("outer false should skip the whole nested body", 1000,
                compiled.invoke("method", Integer.class, false, false).returnValue());
        assertEquals("inner false should skip only the inner body", 1101,
                compiled.invoke("method", Integer.class, true, false).returnValue());
        assertEquals("both true should execute both bodies and both fallthrough statements", 1111,
                compiled.invoke("method", Integer.class, true, true).returnValue());
    }

    private pt.up.fe.comp.test.env.executors.CompilationResult compileSnippet(String code) {
        return compile(toJasminResult(code));
    }

    private JasminResult toJasminResult(String code) {
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
        var jasminResult = jasminBackend.toJasmin(ollirResult);
        assertNotNull("Jasmin result should not be null", jasminResult);
        assertNotNull("Jasmin code should not be null", jasminResult.getJasminCode());
        return jasminResult;
    }
}
