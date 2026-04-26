package core.ollir;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class FieldArrayOllirTest extends JmmTestEnv {

    public FieldArrayOllirTest() {
        super("", "");
    }

    @Test
    public void readingElementFromFieldArrayShouldLoadFieldFirst() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    int[] values;

                    public int method() {
                        return values[0];
                    }
                }""");

        assertTrue("Reading from an array field should use getfield",
                ollirCode.contains("getfield(this, values.array.i32).array.i32"));
        assertTrue("Reading from an array field should index the loaded array",
                ollirCode.contains("[0.i32].i32"));
    }

    @Test
    public void writingElementToFieldArrayShouldLoadFieldFirst() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    int[] values = new int[2];

                    public void method() {
                        values[0] = 1;
                    }
                }""");

        assertTrue("Writing to an array field should use getfield before indexing",
                ollirCode.contains("getfield(this, values.array.i32).array.i32"));
        assertTrue("Writing to an array field should store into an array element",
                ollirCode.contains("[0.i32].i32 :=.i32 1.i32"));
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
