package core.ollir.RegisterAllocation;

import org.junit.Test;
import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.Instruction;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;
import pt.up.fe.comp2026.optimization.liveness.LivenessAnalyzer;

import java.util.List;

public class LivenessTest extends JmmTestEnv {

    public LivenessTest() {
        super("", "");
    }

    private OllirResult getTransformedOllir(String code) {
        var parserResult = parseSnippet(code);
        var analysis = new JmmAnalysisImpl();
        var symbolTableResult = analysis.buildSymbolTable(parserResult);
        var semantics = analysis.semanticAnalysis(symbolTableResult);

        var optimization = new JmmOptimizationImpl();
        var ollirResult = optimization.toOllir(semantics);

        return optimization.transformOllir(ollirResult);
    }

    @Test
    public void testDefUseSimple() {
        var code = """
                package x;
                class A {
                    public int method() {
                        int a;
                        int b;
                        a = 1;
                        b = a;
                        return b;
                    }
                }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        LivenessAnalyzer analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();

        List<Instruction> insts = method.getInstructions();

        boolean foundDefA = false;
        boolean foundDefBUseA = false;
        boolean foundUseB = false;

        for (Instruction inst : insts) {
            var def = analyzer.getDef(inst);
            var use = analyzer.getUse(inst);

            if (def.contains("a") && use.isEmpty()) {
                foundDefA = true;
            }
            if (def.contains("b") && use.contains("a")) {
                foundDefBUseA = true;
            }
            if (use.contains("b")) {
                foundUseB = true;
            }
        }

        assertTrue("Should have an instruction that defs 'a'", foundDefA);
        assertTrue("Should have an instruction that defs 'b' and uses 'a'", foundDefBUseA);
        assertTrue("Should have an instruction that uses 'b'", foundUseB);
    }

    @Test
    public void testDefUseBinaryOp() {
        var code = """
            package x;
            class A {
                public int method() {
                    int a;
                    int b;
                    int c;
                    a = 1;
                    b = 2;
                    c = a + b;
                    return c;
                }
            }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();

        boolean foundUseAB = false;
        boolean foundDefC = false;

        for (Instruction inst : method.getInstructions()) {
            var def = analyzer.getDef(inst);
            var use = analyzer.getUse(inst);

            if (use.contains("a") && use.contains("b")) {
                foundUseAB = true;
            }

            if (def.contains("c")) {
                foundDefC = true;
            }
        }

        assertTrue("There should be an instruction that uses both a and b", foundUseAB);
        assertTrue("Variable c should be defined in the method", foundDefC);
    }

    @Test
    public void testReturnUse() {
        var code = """
            package x;
            class A {
                public int method() {
                    int a;
                    a = 5;
                    return a;
                }
            }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();

        boolean foundReturnUse = false;

        for (Instruction inst : method.getInstructions()) {
            var use = analyzer.getUse(inst);

            if (use.contains("a")) {
                foundReturnUse = true;
            }
        }

        assertTrue("Return should use variable a", foundReturnUse);
    }

    @Test
    public void testVariableOverwrite() {
        var code = """
            package x;
            class A {
                public int method() {
                    int a;
                    a = 1;
                    a = 2;
                    return a;
                }
            }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();

        int defCount = 0;

        for (Instruction inst : method.getInstructions()) {
            if (analyzer.getDef(inst).contains("a")) {
                defCount++;
            }
        }

        assertTrue("Variable a should be defined more than once", defCount >= 2);
    }

    @Test
    public void testUnusedVariable() {
        var code = """
            package x;
            class A {
                public int method() {
                    int a;
                    int b;
                    a = 1;
                    b = 2;
                    return a;
                }
            }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();

        boolean bUsed = false;

        for (Instruction inst : method.getInstructions()) {
            if (analyzer.getUse(inst).contains("b")) {
                bUsed = true;
            }
        }

        assertTrue("Variable b should never be used", !bUsed);
    }

    @Test
    public void testChainedUse() {
        var code = """
            package x;
            class A {
                public int method() {
                    int a;
                    int b;
                    int c;
                    a = 1;
                    b = a;
                    c = b;
                    return c;
                }
            }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();
        analyzer.computeInOut();

        boolean foundBUsesA = false;
        boolean foundCUsesB = false;

        boolean cInOut = false;

        for (Instruction inst : method.getInstructions()) {
            var def = analyzer.getDef(inst);
            var use = analyzer.getUse(inst);

            if (def.contains("b") && use.contains("a")) {
                foundBUsesA = true;
            }
            if (def.contains("c") && use.contains("b")) {
                foundCUsesB = true;
                if (analyzer.getOut(inst).contains("c")) {
                    cInOut = true;
                }
            }
        }

        assertTrue("a", foundBUsesA);
        assertTrue("a", foundCUsesB);
        assertTrue("c should be in OUT after c=b", cInOut);
    }

    @Test
    public void testInOutSequential() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                a = 1;
                b = a;
                return b;
            }
        }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();
        analyzer.computeInOut();

        var insts = method.getInstructions();

        boolean aLiveAfterAssign = false;
        boolean bLiveBeforeReturn = false;

        for (Instruction inst : insts) {
            if (analyzer.getDef(inst).contains("a")) {
                if (analyzer.getOut(inst).contains("a")) {
                    aLiveAfterAssign = true;
                }
            }

            if (analyzer.getUse(inst).contains("b")) {
                if (analyzer.getIn(inst).contains("b")) {
                    bLiveBeforeReturn = true;
                }
            }
        }

        assertTrue("a should be live after a=1", aLiveAfterAssign);
        assertTrue("b should be live before return", bLiveBeforeReturn);
    }

    @Test
    public void testKillDefinition() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                a = 1;
                a = 2;
                return a;
            }
        }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();
        analyzer.computeInOut();

        var insts = method.getInstructions();

        int firstAssignIndex = -1;

        for (int i = 0; i < insts.size(); i++) {
            if (analyzer.getDef(insts.get(i)).contains("a")) {
                firstAssignIndex = i;
                break;
            }
        }

        assertTrue("Old value of a should not be live after reassignment",
                !analyzer.getOut(insts.get(firstAssignIndex)).contains("a"));
    }

    @Test
    public void testIfElseMergeLiveness() {
        var code = """
        package x;
        class A {
            public int method(boolean cond) {
                int a;
                if (cond) {
                    a = 1;
                } else {
                    a = 2;
                }
                return a;
            }
        }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();
        analyzer.computeInOut();

        boolean aLiveBeforeReturn = false;

        for (Instruction inst : method.getInstructions()) {
            if (analyzer.getUse(inst).contains("a")) {
                aLiveBeforeReturn = analyzer.getIn(inst).contains("a");
            }
        }

        assertTrue("a should be live before return after if/else", aLiveBeforeReturn);
    }

    @Test
    public void testWhileLoopLiveness() {
        var code = """
        package x;
        class A {
            public int method() {
                int i;
                i = 0;
                while (i < 10) {
                    i = i + 1;
                }
                return i;
            }
        }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();
        analyzer.computeInOut();

        boolean iLiveInLoop = false;

        for (Instruction inst : method.getInstructions()) {
            if (analyzer.getUse(inst).contains("i") &&
                    analyzer.getIn(inst).contains("i")) {
                iLiveInLoop = true;
            }
        }

        assertTrue("i should be live inside the loop", iLiveInLoop);
    }

    @Test
    public void testDeadVariableNotLive() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                a = 1;
                b = 2;
                return a;
            }
        }""";

        var ollirResult = getTransformedOllir(code);
        ollirResult.getOllirClass().buildCFGs();

        var method = ollirResult.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();
        analyzer.computeInOut();

        boolean bEverLive = false;

        for (Instruction inst : method.getInstructions()) {
            if (analyzer.getIn(inst).contains("b") ||
                    analyzer.getOut(inst).contains("b")) {
                bEverLive = true;
            }
        }

        assertTrue("b should never be live", !bEverLive);
    }

    @Test
    public void testCallUsesArguments() {
        var code = """
        package x;
        class A {
            public int foo(int x) {
                return x;
            }
            public int method() {
                int a;
                int b;
                a = 1;
                b = this.foo(a);
                return b;
            }
        }""";

        var ollirResult = getTransformedOllir(code);
        var classUnit = ollirResult.getOllirClass();
        classUnit.buildCFGs();

        var method = classUnit.getMethods().stream()
                .filter(m -> m.getMethodName().equals("method"))
                .findFirst().get();

        var analyzer = new LivenessAnalyzer(method);
        analyzer.computeDefUse();
        analyzer.computeInOut();

        boolean found = false;

        for (Instruction inst : method.getInstructions()) {
            if (analyzer.getUse(inst).contains("a")) {
                found = true;
            }
        }

        assertTrue("Call should use argument a", found);
    }
}
