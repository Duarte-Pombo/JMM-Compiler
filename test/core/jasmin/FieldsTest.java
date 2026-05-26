package core.jasmin;

import org.junit.Test;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.backend.JasminBackendImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class FieldsTest extends JmmTestEnv {

    public FieldsTest() {
        super("", "");
    }

    @Test
    public void fieldDeclarationShouldEmitIntDescriptor() {
        var jasminCode = toJasminCode("""
                package x;
                class A {
                    int value;
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Field declaration should contain int descriptor", jasminCode.contains(".field public value I"));
    }

    @Test
    public void explicitThisFieldWriteShouldUsePutfield() {
        var jasminCode = toJasminCode("""
                package x;
                class A {
                    int value;

                    public void set() {
                        this.value = 10;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Field write should use putfield", jasminCode.contains("putfield x/A/value I"));
    }

    @Test
    public void explicitThisFieldReadShouldUseGetfield() {
        var jasminCode = toJasminCode("""
                package x;
                class A {
                    int value;

                    public int get() {
                        return this.value;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Field read should use getfield", jasminCode.contains("getfield x/A/value I"));
    }

    @Test
    public void localShadowingFieldShouldStoreLocally() {
        var jasminCode = toJasminCode("""
                package x;
                class A {
                    int value;

                    public void method() {
                        int value;
                        value = 1;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Local assignment should emit store", jasminCode.contains("store"));
        assertTrue("Local assignment should not use putfield", !jasminCode.contains("putfield x/A/value I"));
    }

    @Test
    public void thisAccessToUndeclaredFieldShouldFailSemantics() {
        var parserResult = parseSnippet("""
                package x;
                class A {
                    public void method() {
                        int a;
                        this.a = 10;
                    }
                }""");

        assertTrue("Unexpected parser errors", parserResult.getReports(ReportType.ERROR).isEmpty());

        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);

        assertTrue("Expected semantic errors for this access to undeclared field", !semantics.getReports(ReportType.ERROR).isEmpty());
    }

    @Test
    public void explicitThisShouldBypassShadowing() {
        var jasminCode = toJasminCode("""
            package x;
            class A {
                int value;

                public void method() {
                    int value;
                    this.value = 7;
                }
            }""");

        assertTrue("",jasminCode.contains("putfield x/A/value I"));
    }

    @Test
    public void assignFieldToLocalShouldUseGetfield() {
        var jasminCode = toJasminCode("""
            package x;
            class A {
                int value;

                public void method() {
                    int a;
                    a = this.value;
                }
            }""");

        assertTrue("",jasminCode.contains("getfield x/A/value I"));
        assertTrue("",jasminCode.contains("store"));
    }

    @Test
    public void assignLocalToFieldShouldUsePutfield() {
        var jasminCode = toJasminCode("""
                package x;
                class A {
                    int value;
                
                    public void method() {
                        int a;
                        this.value = a;
                    }
                }""");
e
        assertTrue("", jasminCode.contains("putfield x/A/value I"));

    }

    @Test
    public void fieldIncrementShouldNotUseIinc() {
        var jasminCode = toJasminCode("""
            package x;
            class A {
                int value;

                public void method() {
                    this.value = this.value + 1;
                }
            }""");

        assertTrue("",!jasminCode.contains("iinc "));
        assertTrue("",jasminCode.contains("getfield"));
        assertTrue("",jasminCode.contains("putfield"));
    }

    @Test
    public void booleanFieldShouldUseZDescriptor() {
        var jasminCode = toJasminCode("""
            package x;
            class A {
                boolean flag;
            }""");

        assertTrue("",jasminCode.contains(".field public flag Z"));
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
