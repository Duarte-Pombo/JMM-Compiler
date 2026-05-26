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

import static org.junit.Assert.*;

public class IincSelectionTest extends JmmTestEnv {

    public IincSelectionTest() {
        super("", "");
    }

    @Test
    public void iincInRangeFromJmm() {
        var jasminCode = toJasminCodeFromJmm("""
                package x;
                class A {
                    public void method() {
                        int a;
                        a = a - 128;
                    }
                }""");
        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("In-range update should use iinc", jasminCode.contains("iinc "));
    }

    @Test
    public void iincOutOfRangeFromJmm() {
        var jasminCode = toJasminCodeFromJmm("""
                package x;
                class A {
                    public void method() {
                        int a;
                        a = a + 128;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertFalse("Out-of-range update should not use iinc", jasminCode.contains("iinc "));
    }

    @Test
    public void iincInRangeFromOllir() {
        var ollirCode = """
                package x;

                A extends Object {
                    .construct \"<init>\"().V {
                        invokespecial(this.\"java.lang.Object\", \"<init>\").V;
                    }

                    .method public method().V {
                        a.i32 :=.i32 a.i32 -.i32 128.i32;
                        ret.V;
                    }
                }
                """;

        var jasminCode = toJasminCodeFromOllir(ollirCode);

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("In-range update should use iinc with -128", jasminCode.contains("iinc 1 -128"));
    }

    @Test
    public void iincOutOfRangeFromOllir() {
        var ollirCode = """
                package x;

                A extends Object {
                    .construct \"<init>\"().V {
                        invokespecial(this.\"java.lang.Object\", \"<init>\").V;
                    }

                    .method public method().V {
                        a.i32 :=.i32 a.i32 +.i32 128.i32;
                        ret.V;
                    }
                }
                """;

        var jasminCode = toJasminCodeFromOllir(ollirCode);

        assertNotNull("Jasmin should be produced", jasminCode);
        assertFalse("Out-of-range update should not use iinc", jasminCode.contains("iinc "));
    }
    @Test
    public void addConstantOnLeftShouldUseIinc() {
        var ollirCode = """
                package x;

                A extends Object {
                    .construct \"<init>\"().V {
                        invokespecial(this.\"java.lang.Object\", \"<init>\").V;
                    }

                    .method public method().V {
                        a.i32 :=.i32 -2.i32 +.i32 a.i32;
                        ret.V;
                    }
                }
                """;

        var jasminCode = toJasminCodeFromOllir(ollirCode);

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Addition with constant on left should use iinc", jasminCode.contains("iinc "));
    }




    private String toJasminCodeFromJmm(String code) {
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
        System.out.println("Generated OLLIR:\n" + ollirResult.getOllirCode());
        var jasminBackend = new JasminBackendImpl();
        var jasminResult = jasminBackend.toJasmin(ollirResult);
        assertNotNull("Jasmin result should not be null", jasminResult);
        return jasminResult.getJasminCode();
    }
}
