package core.ollir;

import org.junit.Test;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.lexer.JmmLexerImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;
import pt.up.fe.comp2026.parser.JmmParserImpl;

import java.util.Map;

import static org.junit.Assert.*;

public class DeadCodeEliminationRegressionTest {

    @Test
    public void unreadAssignmentShouldKeepPrefixIncrementSideEffect() {
        var unoptimized = toOllirCode("""
                package x;
                class A {
                    public int method(int start) {
                        int y;
                        int unused;
                        y = start;
                        unused = ++y;
                        return y;
                    }
                }""", false);

        var optimized = toOllirCode("""
                package x;
                class A {
                    public int method(int start) {
                        int y;
                        int unused;
                        y = start;
                        unused = ++y;
                        return y;
                    }
                }""", true);

        assertTrue("Sanity check: unoptimized code should contain the increment",
                unoptimized.contains("+.i32 1.i32"));
        assertTrue("Removing the unused assignment must still preserve the ++y side effect",
                optimized.contains("+.i32 1.i32"));
    }

    @Test
    public void arrayWriteThroughLocalAliasOfParameterShouldBePreserved() {
        var optimized = toOllirCode("""
                package x;
                class A {
                    public int method(int[] input) {
                        int[] alias;
                        alias = input;
                        alias[0] = 7;
                        return 0;
                    }
                }""", true);

        assertTrue("Array writes through a local alias of a parameter may be visible to the caller",
                optimized.contains("[0.i32].i32 :=.i32 7.i32"));
    }

    @Test
    public void overwrittenStoreShouldBeRemovedEvenIfVariableIsReadLater() {
        var optimized = toOllirCode("""
                package x;
                class A {
                    public int method(int first, int second) {
                        int value;
                        value = first;
                        value = second;
                        return value;
                    }
                }""", true);

        assertFalse("The first store is overwritten before any read and should be removed",
                optimized.contains("value.i32 :=.i32 first.i32"));
        assertTrue("The live store that feeds the return should remain",
                optimized.contains("value.i32 :=.i32 second.i32"));
    }

    @Test
    public void statementAfterReturnShouldBeEliminatedEvenWithFieldWrite() {
        var optimized = toOllirCode("""
                package x;
                class A {
                    int field;

                    public int method() {
                        return 0;
                        field = 1;
                    }
                }""", true);

        assertFalse("A statement after return is unreachable and should not emit a field write",
                optimized.contains("putfield(this, field.i32, 1.i32).V"));
    }

    @Test
    public void whileFalseBodyShouldBeEliminated() {
        var optimized = toOllirCode("""
                package x;
                class A {
                    int field;

                    public int method() {
                        while (false) {
                            field = 1;
                        }
                        return 0;
                    }
                }""", true);

        assertFalse("The body of while(false) is unreachable and should not emit a field write",
                optimized.contains("putfield(this, field.i32, 1.i32).V"));
    }

    private String toOllirCode(String code, boolean optimize) {
        var config = optimize ? Map.of("optimize", "true") : Map.<String, String>of();

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
        assertNotNull("Ollir code should not be null", ollirResult.getOllirCode());
        return ollirResult.getOllirCode();
    }
}
