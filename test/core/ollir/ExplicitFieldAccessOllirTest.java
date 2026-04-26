package core.ollir;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class ExplicitFieldAccessOllirTest extends JmmTestEnv {

    public ExplicitFieldAccessOllirTest() {
        super("", "");
    }

    @Test
    public void readingExplicitThisFieldShouldGenerateGetField() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    int value;

                    public int method() {
                        return this.value;
                    }
                }""");

        assertTrue("Reading this.field should use getfield",
                ollirCode.contains("getfield(this"));
        assertTrue("Reading this.field should reference the field name and type",
                ollirCode.contains("value.i32"));
    }

    @Test
    public void writingExplicitThisFieldShouldGeneratePutField() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    int value;

                    public void method() {
                        this.value = 1;
                    }
                }""");

        assertTrue("Writing this.field should use putfield",
                ollirCode.contains("putfield(this"));
        assertTrue("Writing this.field should reference the field name and assigned value",
                ollirCode.contains("value.i32, 1.i32"));
    }

    @Test
    public void readingObjectFieldShouldNotBeImplicitThis() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    int value;

                    public int method() {
                        A other;
                        other = new A();
                        return other.value;
                    }
                }""");

        assertTrue("Reading obj.field should use getfield",
                ollirCode.contains("getfield("));
        assertTrue("Reading obj.field should reference the field name and type",
                ollirCode.contains("value.i32"));
        assertTrue("Reading obj.field should not be lowered as implicit this.field",
                !ollirCode.contains("getfield(this"));
    }

    @Test
    public void writingObjectFieldShouldNotBeImplicitThis() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    int value;

                    public void method() {
                        A other;
                        other = new A();
                        other.value = 1;
                    }
                }""");

        assertTrue("Writing obj.field should use putfield",
                ollirCode.contains("putfield("));
        assertTrue("Writing obj.field should reference the field name and assigned value",
                ollirCode.contains("value.i32, 1.i32"));
        assertTrue("Writing obj.field should not be lowered as implicit this.field",
                !ollirCode.contains("putfield(this"));
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
