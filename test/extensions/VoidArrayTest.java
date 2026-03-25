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
        // We use parseSnippetWithErrors in case the Grammar rejects it first.
        // If the grammar allows it, analyzeSnippet will catch it via your semantic check!
        String code = """
                package x;
                class A {
                    public int m() {
                        void[] badArray;
                        return 0;
                    }
                }""";

        var parserResult = parseSnippet(code);

        // If the parser let it through, the semantic analyzer MUST catch it.
        if (parserResult.getReports(ReportType.ERROR).isEmpty()) {
            var analysis = new JmmAnalysisImpl();
            var symbolTableResult = analysis.buildSymbolTable(parserResult);
            var semantics = analysis.semanticAnalysis(symbolTableResult);

            var errors = semantics.getReports(ReportType.ERROR);
            assertTrue("Expected a semantic error for void[]", !errors.isEmpty());
            assertTrue("Expected void base type message",
                       errors.get(0).getMessage().contains("void"));
        } else {
            // The parser successfully blocked it
            assertTrue("true",true);
        }
    }
}