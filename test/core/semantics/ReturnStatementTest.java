package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

public class ReturnStatementTest extends JmmTestEnv {

    public ReturnStatementTest() {
        super("", "");
    }

    @Test
    public void regularMainMethodNameShouldNotBeTreatedAsEntrypoint() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    int main() {
                        return 1;
                    }
                }""");

        assertEquals("Expected 0 errors", 0, semantics.getReports(ReportType.ERROR).size());
    }

    @Test
    public void specialMainShouldAllowEmptyReturn() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public static void main(String[] args) {
                        return;
                    }
                }""");

        assertEquals("Expected 0 errors", 0, semantics.getReports(ReportType.ERROR).size());
    }

    @Test
    public void voidMethodShouldRejectReturnValue() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    void foo() {
                        return 1;
                    }
                }""");

        assertEquals("Expected 1 error", 1, semantics.getReports(ReportType.ERROR).size());
    }

    private pt.up.fe.comp.jmm.analysis.JmmSemanticsResult analyzeSnippet(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(symbolTableResult);
    }
}
