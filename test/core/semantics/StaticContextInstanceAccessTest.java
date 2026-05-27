package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class StaticContextInstanceAccessTest extends JmmTestEnv {

    public StaticContextInstanceAccessTest() {
        super("", "");
    }

    @Test
    public void classQualifiedInstanceFieldReadInStaticMethodShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int f;
                    public static int m() {
                        return A.f;
                    }
                }""");

        assertTrue("Expected semantic error for instance field access from static context",
                !errorReports(semantics).isEmpty());
    }

    @Test
    public void classQualifiedInstanceFieldWriteInStaticMethodShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int f;
                    public static int m() {
                        A.f = 1;
                        return 0;
                    }
                }""");

        assertTrue("Expected semantic error for instance field assignment from static context",
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
