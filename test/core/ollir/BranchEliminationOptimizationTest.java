package core.ollir;

import org.junit.Test;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.inst.CondBranchInstruction;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.lexer.JmmLexerImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;
import pt.up.fe.comp2026.parser.JmmParserImpl;

import java.util.Map;

import static org.junit.Assert.*;

public class BranchEliminationOptimizationTest {

    @Test
    public void falseIfWithoutElseShouldBeEliminated() {
        var classUnit = toOptimizedOllirClass("""
                package x;
                class A {
                    public int method(int v) {
                        int res;
                        res = 10;
                        if (false) {
                            res = v;
                        }
                        return res;
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var condCount = method.getInstructions().stream().filter(CondBranchInstruction.class::isInstance).count();
        assertEquals("Constant false if without else should not generate conditional branches", 0, condCount);
    }

    private ClassUnit toOptimizedOllirClass(String code) {
        var config = Map.of("optimize", "true");

        var lexer = new JmmLexerImpl();
        var lexerResult = lexer.lex(code, config);
        assertTrue("Unexpected lexical errors", lexerResult.getReports(ReportType.ERROR).isEmpty());

        var parser = new JmmParserImpl();
        var parserResult = parser.parse(lexerResult, config);
        assertTrue("Unexpected parser errors", parserResult.getReports(ReportType.ERROR).isEmpty());

        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);
        assertTrue("Unexpected semantic errors", semantics.getReports(ReportType.ERROR).isEmpty());

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);
        assertTrue("Unexpected optimization errors", ollirResult.getReports(ReportType.ERROR).isEmpty());
        assertNotNull("Ollir class unit should not be null", ollirResult.getOllirClass());
        return ollirResult.getOllirClass();
    }
}
