package core.semantics;

import org.junit.Test;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;

import java.util.List;

public class AssignmentsTest extends JmmTestEnv {

    public AssignmentsTest() {
        super("", "");
    }

    @Test
    public void intAssignmentShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        int a;
                        int b;
                        a = b;
                    }
                }""");

        assertEquals("aaa",0, assignmentErrors(semantics).size());
    }
    @Test
    public void intBooleanAssignmentShouldFail() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        int a;
                        boolean b;
                        a = b;
                    }
                }""");

        var errors = assignmentErrors(semantics);
        assertEquals("aaa",1, errors.size());
        assertTrue("aaa",errors.get(0).getMessage().contains("Cannot assign expression of type"));
    }

    @Test
    public void booleanAssignmentShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        boolean a;
                        boolean b;
                        a = b;
                    }
                }""");

        assertEquals("aaa",0, assignmentErrors(semantics).size());
    }

    @Test
    public void expressionAssignmentShouldPass() {
        var semantics = analyzeSnippet("""
                package x;
                class A {
                    public void m() {
                        int a;
                        int b;
                        a = b + 1;
                    }
                }""");

        assertEquals("aaa",0, assignmentErrors(semantics).size());
    }

    private JmmSemanticsResult analyzeSnippet(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        return analysis.semanticAnalysis(symbolTableResult);
    }

    private List<Report> assignmentErrors(JmmSemanticsResult semantics) {
        return semantics.getReports(ReportType.ERROR).stream()
                .filter(r -> r.getMessage().contains("Cannot assign expression of type"))
                .toList();
    }
}