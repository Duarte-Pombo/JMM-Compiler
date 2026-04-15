package core.ollir;

import org.junit.Test;
import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.inst.ReturnInstruction;
import org.specs.comp.ollir.type.BuiltinKind;
import org.specs.comp.ollir.type.BuiltinType;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;

public class ReturnStatementOllirTest extends JmmTestEnv {

    public ReturnStatementOllirTest() {
        super("", "");
    }

    @Test
    public void intMethodShouldGenerateIntReturnInstruction() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    public int method() {
                        return 5;
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var returns = method.getInstructions().stream()
                .filter(ReturnInstruction.class::isInstance)
                .map(ReturnInstruction.class::cast)
                .toList();

        assertEquals("Method should have exactly one return instruction", 1, returns.size());
        assertTrue("Return must be i32", BuiltinType.is(returns.getFirst().getReturnType(), BuiltinKind.INT32));
        assertTrue("Non-void return should carry an operand", returns.getFirst().getOperand().isPresent());
    }

    @Test
    public void booleanMethodShouldGenerateBooleanReturnInstruction() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    public boolean method() {
                        return true;
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var returns = method.getInstructions().stream()
                .filter(ReturnInstruction.class::isInstance)
                .map(ReturnInstruction.class::cast)
                .toList();

        assertEquals("Method should have exactly one return instruction", 1, returns.size());
        assertTrue("Return must be bool", BuiltinType.is(returns.getFirst().getReturnType(), BuiltinKind.BOOLEAN));
        assertTrue("Non-void return should carry an operand", returns.getFirst().getOperand().isPresent());
    }

    @Test
    public void voidMethodWithoutExplicitReturnShouldGenerateVoidReturnInstruction() {
        var classUnit = toOllirClass("""
                package x;
                class A {
                    public void method() {
                        int x;
                        x = 1;
                    }
                }""");

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst()
                .orElseThrow();

        var returns = method.getInstructions().stream()
                .filter(ReturnInstruction.class::isInstance)
                .map(ReturnInstruction.class::cast)
                .toList();

        assertEquals("Method should have exactly one return instruction", 1, returns.size());
        assertTrue("Return must be void", BuiltinType.is(returns.getFirst().getReturnType(), BuiltinKind.VOID));
        assertTrue("Void return should not carry an operand", returns.getFirst().getOperand().isEmpty());
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
