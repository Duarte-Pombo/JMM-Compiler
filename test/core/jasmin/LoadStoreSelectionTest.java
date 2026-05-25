package core.jasmin;

import org.junit.Test;
import org.specs.comp.ollir.Ollir;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.backend.JasminBackendImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class LoadStoreSelectionTest extends JmmTestEnv {

    public LoadStoreSelectionTest() {
        super("", "");
    }

    @Test
    public void loadStoreShortAndLongFormsFromJmm() {
        var jasminCode = toJasminCodeFromJmm("""
                package x;
                class A {
                    int f;
                    public int method(int a, int b, int c, int d) {
                        b = a;
                        int e;
                        e = c + b;
                        this.f = b;
                        return d + e;
                    }
                }""");

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Should use short load for param c (index 3)", jasminCode.contains("iload_3"));
        assertTrue("Should use long load for param d (index 4)", jasminCode.contains("iload 4"));
        assertTrue("Should use short store for param b (index 2)", jasminCode.contains("istore_2"));
        assertTrue("Should use long store for local e (index 5)", jasminCode.contains("istore 5"));
        assertTrue("Should use short load for this", jasminCode.contains("aload_0"));
        assertFalse("Should not use short form for index 4", jasminCode.contains("iload_4"));
        assertFalse("Should not use short form for index 5", jasminCode.contains("istore_5"));
    }

    @Test
    public void loadStoreShortAndLongFormsFromOllir() {
        var ollirCode = """
                package x;

                A extends Object {
                    .field f.i32;

                    .construct \"<init>\"().V {
                        invokespecial(this.\"java.lang.Object\", \"<init>\").V;
                    }

                    .method public method(a.i32, b.i32, c.i32, d.i32).i32 {
                        b.i32 :=.i32 a.i32;
                        e.i32 :=.i32 c.i32 +.i32 b.i32;
                        putfield(this, f.i32, b.i32).V;
                        ret.i32 d.i32;
                    }
                }
                """;

        var jasminCode = toJasminCodeFromOllir(ollirCode);

        assertNotNull("Jasmin should be produced", jasminCode);
        assertTrue("Should use short load for param c (index 3)", jasminCode.contains("iload_3"));
        assertTrue("Should use long load for param d (index 4)", jasminCode.contains("iload 4"));
        assertTrue("Should use short store for param b (index 2)", jasminCode.contains("istore_2"));
        assertTrue("Should use long store for local e (index 5)", jasminCode.contains("istore 5"));
        assertTrue("Should use short load for this", jasminCode.contains("aload_0"));
        assertFalse("Should not use short form for index 4", jasminCode.contains("iload_4"));
        assertFalse("Should not use short form for index 5", jasminCode.contains("istore_5"));
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
