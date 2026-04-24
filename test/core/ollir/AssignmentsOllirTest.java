package core.ollir;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class AssignmentsOllirTest extends JmmTestEnv {

    public AssignmentsOllirTest() {
        super("", "");
    }

    @Test
    public void localAssignmentShouldGenerateDirectStore() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    public void method() {
                        int a;
                        a = 0;
                    }
                }""");

        assertTrue("Local assignment should emit a direct store", ollirCode.contains("a.i32 :=.i32 0.i32"));
        assertTrue("Local assignment should not use putfield", !ollirCode.contains("putfield("));
    }

    @Test
    public void fieldAssignmentShouldGeneratePutField() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    int a;

                    public void method() {
                        a = 0;
                    }
                }""");

        assertTrue("Field assignment should use putfield", ollirCode.contains("putfield(this, a.i32, 0.i32).V"));
    }

    @Test
    public void fieldShadowedByLocalShouldStayLocal() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    int a;

                    public void method() {
                        int a;
                        a = 1;
                    }
                }""");

        assertTrue("Shadowed local variable should be assigned directly", ollirCode.contains("a.i32 :=.i32 1.i32"));
        assertTrue("Shadowed local variable should not be lowered to putfield", !ollirCode.contains("putfield(this, a.i32, 1.i32).V"));
    }

    @Test
    public void fieldShadowedByParameterShouldStayLocal() {
        var ollirCode = toOllirCode("""
                package x;
                class A {
                    int a;

                    public void method(int a) {
                        a = 2;
                    }
                }""");

        assertTrue("Shadowed parameter should be assigned directly", ollirCode.contains("a.i32 :=.i32 2.i32"));
        assertTrue("Shadowed parameter should not be lowered to putfield", !ollirCode.contains("putfield(this, a.i32, 2.i32).V"));
    }

    @Test
    public void importedSupertypeAssignmentShouldKeepClassType() {
        var ollirCode = toOllirCode("""
                package x;
                import examples.Quicksort;
                class A extends Quicksort {
                    public void method() {
                        Quicksort q;
                        q = this;
                    }
                }""");

        assertTrue("Assignment to imported supertype should keep the type in OLLIR", ollirCode.contains("q.Quicksort :=.Quicksort this.A"));
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
