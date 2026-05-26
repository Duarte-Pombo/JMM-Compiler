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

public class LdcSelectionTest extends JmmTestEnv {

    public LdcSelectionTest() {
        super("", "");
    }

    @Test
    public void ldcSelectionFromJmm() {
        var jasminCode = toJasminCodeFromJmm("""
                package x;
                class A {
                    public int method() {
                        int a;
                        int b;
                        int c;
                        int d;
                        int e;
                        int f;
                        a = 1;
                        b = 0;
                        c = 5;
                        d = 6;
                        e = 200;
                        f = 40000;
                        return f;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Should use iconst_1 for 1", jasminCode.contains("iconst_1"));
        assertTrue("Should use iconst_0 for 0", jasminCode.contains("iconst_0"));
        assertTrue("Should use iconst_5 for 5", jasminCode.contains("iconst_5"));
        assertTrue("Should use bipush for 6", jasminCode.contains("bipush 6"));
        assertTrue("Should use sipush for 200", jasminCode.contains("sipush 200"));
        assertTrue("Should use ldc for 40000", jasminCode.contains("ldc 40000"));
        assertFalse("Should not use ldc for 0", jasminCode.contains("ldc 0"));
        assertFalse("Should not use ldc for 1", jasminCode.contains("ldc 1"));
        assertFalse("Should not use ldc for 5", jasminCode.contains("ldc 5"));
    }

    @Test
    public void ldcBoundarySelectionFromJmm() {
        var jasminCode = toJasminCodeFromJmm("""
                package x;
                class A {
                    public int method() {
                        int a;
                        int b;
                        int c;
                        int d;
                        a = 127;
                        b = 128;
                        c = 32767;
                        d = 32768;
                        return d;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Should use bipush for 127", jasminCode.contains("bipush 127"));
        assertTrue("Should use sipush for 128", jasminCode.contains("sipush 128"));
        assertTrue("Should use sipush for 32767", jasminCode.contains("sipush 32767"));
        assertTrue("Should use ldc for 32768", jasminCode.contains("ldc 32768"));
    }

    @Test
    public void ldcSelectionFromOllir() {
        var ollirCode = """
                package x;

                A extends Object {
                    .construct \"<init>\"().V {
                        invokespecial(this.\"java.lang.Object\", \"<init>\").V;
                    }

                    .method public method().i32 {
                        a.i32 :=.i32 -1.i32;
                        b.i32 :=.i32 0.i32;
                        c.i32 :=.i32 5.i32;
                        d.i32 :=.i32 6.i32;
                        e.i32 :=.i32 200.i32;
                        f.i32 :=.i32 40000.i32;
                        ret.i32 f.i32;
                    }
                }
                """;

        var jasminCode = toJasminCodeFromOllir(ollirCode);

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Should use iconst_m1 for -1", jasminCode.contains("iconst_m1"));
        assertTrue("Should use iconst_0 for 0", jasminCode.contains("iconst_0"));
        assertTrue("Should use iconst_5 for 5", jasminCode.contains("iconst_5"));
        assertTrue("Should use bipush for 6", jasminCode.contains("bipush 6"));
        assertTrue("Should use sipush for 200", jasminCode.contains("sipush 200"));
        assertTrue("Should use ldc for 40000", jasminCode.contains("ldc 40000"));
        assertFalse("Should not use ldc for 0", jasminCode.contains("ldc 0"));
        assertFalse("Should not use ldc for 1", jasminCode.contains("ldc 1"));
        assertFalse("Should not use ldc for 5", jasminCode.contains("ldc 5"));
    }

    @Test
    public void ldcBoundarySelectionFromOllir() {
        var ollirCode = """
                package x;

                A extends Object {
                    .construct \"<init>\"().V {
                        invokespecial(this.\"java.lang.Object\", \"<init>\").V;
                    }

                    .method public method().i32 {
                        a.i32 :=.i32 -128.i32;
                        b.i32 :=.i32 -129.i32;
                        c.i32 :=.i32 -32768.i32;
                        d.i32 :=.i32 -32769.i32;
                        ret.i32 b.i32;
                    }
                }
                """;

        var jasminCode = toJasminCodeFromOllir(ollirCode);

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Should use bipush for -128", jasminCode.contains("bipush -128"));
        assertTrue("Should use sipush for -129", jasminCode.contains("sipush -129"));
        assertTrue("Should use sipush for -32768", jasminCode.contains("sipush -32768"));
        assertTrue("Should use ldc for -32769", jasminCode.contains("ldc -32769"));

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
        var jasminBackend = new JasminBackendImpl();
        var jasminResult = jasminBackend.toJasmin(ollirResult);
        assertNotNull("Jasmin result should not be null", jasminResult);
        return jasminResult.getJasminCode();
    }
}
