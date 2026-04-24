package core.ollir;

import org.junit.Test;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.inst.CondBranchInstruction;
import org.specs.comp.ollir.inst.GotoInstruction;
import org.specs.comp.ollir.inst.ReturnInstruction;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class IfElseStatementOllirTest extends JmmTestEnv {

    public IfElseStatementOllirTest() {
        super("", "");
    }

    @Test
    public void simpleIfElseShouldGenerateBranchesAndGotos() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    public int method(int a) {
                        if (a < 10) {
                            a = a + 1;
                        } else {
                            a = a - 1;
                        }
                        return a;
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var condCount = method.getInstructions().stream()
                .filter(CondBranchInstruction.class::isInstance)
                .count();

        var gotoCount = method.getInstructions().stream()
                .filter(GotoInstruction.class::isInstance)
                .count();

        assertTrue("If-else should generate a conditional branch", condCount >= 1);
        assertTrue("If-else should generate gotos", gotoCount >= 1);
    }

    @Test
    public void nestedIfElseShouldGenerateMultipleBranches() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    public int method(int a) {
                        if (a < 10) {
                            if (a < 5) {
                                a = 1;
                            } else {
                                a = 2;
                            }
                        } else {
                            a = 3;
                        }
                        return a;
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var condCount = method.getInstructions().stream()
                .filter(CondBranchInstruction.class::isInstance)
                .count();

        var gotoCount = method.getInstructions().stream()
                .filter(GotoInstruction.class::isInstance)
                .count();

        assertTrue("Nested if-else should generate multiple conditional branches", condCount >= 2);
        assertTrue("Nested if-else should generate multiple gotos", gotoCount >= 2);
    }

    @Test
    public void ifElseShouldStillReturnCorrectly() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    public int method(int a) {
                        if (a < 0) {
                            a = 10;
                        } else {
                            a = 20;
                        }
                        return a;
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var returnCount = method.getInstructions().stream()
                .filter(ReturnInstruction.class::isInstance)
                .count();

        assertTrue("Method should contain a return instruction", returnCount >= 1);
    }

    @Test
    public void ifElseWithEmptyElseShouldStillGenerateFlow() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    public int method(int a) {
                        if (a < 10) {
                            a = 1;
                        } else {
                        }
                        return a;
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var condCount = method.getInstructions().stream()
                .filter(CondBranchInstruction.class::isInstance)
                .count();

        var gotoCount = method.getInstructions().stream()
                .filter(GotoInstruction.class::isInstance)
                .count();

        assertTrue("If without else body should still generate condition", condCount >= 1);
        assertTrue("If without else body should still generate goto", gotoCount >= 1);
    }

    private ClassUnit toOllirClass(String code) {
        var parserResult = parseSnippet(code);
        assertTrue("Unexpected parser errors", parserResult.getReports(ReportType.ERROR).isEmpty());

        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);
        assertTrue("Unexpected semantic errors", semantics.getReports(ReportType.ERROR).isEmpty());

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);

        var classUnit = ollirResult.getOllirClass();
        assertNotNull("Ollir class unit should not be null", classUnit);

        return classUnit;
    }
}