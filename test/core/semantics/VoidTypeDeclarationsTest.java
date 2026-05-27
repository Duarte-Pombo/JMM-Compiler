package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class VoidTypeDeclarationsTest extends JmmTestEnv {

    public VoidTypeDeclarationsTest() {
        super("", "");
    }

    @Test
    public void voidFieldDeclarationShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    void f;
                    public int m() {
                        return 0;
                    }
                }""");

        assertTrue("Expected semantic error for void field declaration",
                !errorReports(semantics).isEmpty());
    }

    @Test
    public void voidLocalDeclarationShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m() {
                        void x;
                        return 0;
                    }
                }""");

        assertTrue("Expected semantic error for void local declaration",
                !errorReports(semantics).isEmpty());
    }

    @Test
    public void voidParameterDeclarationShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public int m(void x) {
                        return 0;
                    }
                }""");

        assertTrue("Expected semantic error for void parameter declaration",
                !errorReports(semantics).isEmpty());
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
