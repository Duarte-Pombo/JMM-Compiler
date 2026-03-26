package extensions;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;


public class VoidArrayTest extends JmmTestEnv {

    public VoidArrayTest() {
        super("", "");
    }

    @Test
    public void voidArrayDeclarationShouldFail() {
        String code = """
                package x;
                class A {
                    public int m() {
                        void[] badArray;
                        return 0;
                    }
                }""";

        var parserResult = parseSnippet(code);

        if (parserResult.getReports(ReportType.ERROR).isEmpty()) {
            var analysis = new JmmAnalysisImpl();
            var symbolTableResult = analysis.buildSymbolTable(parserResult);
            var semantics = analysis.semanticAnalysis(symbolTableResult);

            var errors = semantics.getReports(ReportType.ERROR);
            assertTrue("Expected a semantic error for void[]", !errors.isEmpty());
            assertTrue("Expected void base type message",
                       errors.getFirst().getMessage().contains("void"));
        } else {
            assertTrue("true",true);
        }
    }
}