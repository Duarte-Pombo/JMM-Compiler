package core.jasmin;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp2026.backend.JasminBackendImpl;

public class AssignmentsTest extends JmmTestEnv {

    public AssignmentsTest() {
        super("", "");
    }

    @Test
    public void localAssignmentShouldGenerateDirectStore() {
        var jasminCode = toJasminCode("""
                package x;
                class A {
                    public void method() {
                        int a;
                        a = 0;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Local assignment should emit a store instruction", jasminCode.contains("store"));
        assertTrue("Local assignment should not use putfield", !jasminCode.contains("putfield"));
    }

    @Test
    public void incrementAssignmentShouldUseIinc() {
        var jasminCode = toJasminCode("""
                package x;
                class A {
                    public void method() {
                        int a;
                        a = a + 1;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Increment assignment should use iinc", jasminCode.contains("iinc "));
    }

    @Test
    public void addConstantOnLeftShouldUseIinc() {
        var jasminCode = toJasminCode("""
                package x;
                class A {
                    public void method() {
                        int a;
                        a = 2 + a;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Addition with constant on left should use iinc", jasminCode.contains("iinc "));
    }

    @Test
    public void subConstantOnLeftShouldUseIinc() {
        var jasminCode = toJasminCode("""
                package x;
                class A {
                    public void method() {
                        int a;
                        a = 2 - a;
                    }
                }""");
        assertTrue("Addition with constant on left should not use iinc", !jasminCode.contains("iinc "));
    }

    @Test
    public void subtractConstantShouldUseNegativeIinc() {
        var jasminCode = toJasminCode("""
                package x;
                class A {
                    public void method() {
                        int a;
                        a = a - 2;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Subtraction assignment should use iinc", jasminCode.contains("iinc "));
        assertTrue("Subtraction should use negative value", jasminCode.contains("-2") || jasminCode.contains("iinc "));
    }

    @Test
    public void differentVariableShouldNotUseIinc() {
        var jasminCode = toJasminCode("""
            package x;
            class A {
                public void method() {
                    int a;
                    int b;
                    a = b + 1;
                }
            }""");

        assertTrue("No iinc present",!jasminCode.contains("iinc "));
    }

    @Test
    public void addShouldNotUseIinc() {
        var jasminCode = toJasminCode("""
            package x;
            class A {
                public void method() {
                    int a;
                    int b;
                    a = a + 1000;
                }
            }""");
        assertTrue("No iinc present",!jasminCode.contains("iinc "));
    }

    @Test
    public void subShouldNotUseIinc() {
        var jasminCode = toJasminCode("""
            package x;
            class A {
                public void method() {
                    int a;
                    int b;
                    a = a - 1000;
                }
            }""");
        assertTrue("No iinc present",!jasminCode.contains("iinc "));
    }

    @Test
    public void multShouldNotUseIinc() {
        var jasminCode = toJasminCode("""
            package x;
            class A {
                public void method() {
                    int a;
                    int b;
                    a = a * 10;
                }
            }""");

        assertTrue("No iinc present",!jasminCode.contains("iinc "));
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
