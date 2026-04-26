package core.ollir.RegisterAllocation;

import org.junit.Test;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;
import pt.up.fe.comp2026.optimization.liveness.LivenessAnalyzer;
import pt.up.fe.comp2026.optimization.liveness.InterferenceGraph;
import pt.up.fe.comp.test.env.JmmTestEnv;

public class InterferenceGraphTest extends JmmTestEnv {

    public InterferenceGraphTest() {
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
    public void testSimpleInterference() {
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
        analyzer.computeInOut();

        var graph = new InterferenceGraph(method, analyzer);
        graph.buildGraph();

        assertTrue("a and b should interfere", graph.getNeighbors("a").contains("b"));
        assertTrue("b and a should interfere", graph.getNeighbors("b").contains("a"));
    }

    @Test
    public void testSequentialNoInterference() {
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

        var graph = new InterferenceGraph(method, analyzer);
        graph.buildGraph();

        assertTrue("a and b should not interfere", !graph.getNeighbors("a").contains("b"));
    }

    @Test
    public void testChainInterference() {
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

        var graph = new InterferenceGraph(method, analyzer);
        graph.buildGraph();

        assertTrue("a and b should NOT interfere", !graph.getNeighbors("a").contains("b"));
        assertTrue("b and c should NOT interfere", !graph.getNeighbors("b").contains("c"));
    }

    @Test
    public void testKillRemovesInterference() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                a = 1;
                a = 2;
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

        var graph = new InterferenceGraph(method, analyzer);
        graph.buildGraph();

        assertTrue("a",!graph.getNeighbors("a").contains("a"));
    }

    @Test
    public void testIfElseInterference() {
        var code = """
        package x;
        class A {
            public int method(boolean cond) {
                int a;
                int b;
                int c;
                if (cond) {
                    a = 1;
                    c = a;
                } else {
                    b = 2;
                    c = b;
                }
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

        var graph = new InterferenceGraph(method, analyzer);
        graph.buildGraph();

        assertTrue("a and b should not interfere", !graph.getNeighbors("a").contains("b"));
    }

    @Test
    public void testWhileInterference() {
        var code = """
        package x;
        class A {
            public int method() {
                int i;
                int tmp;
                i = 0;
                while (i < 10) {
                    tmp = i;
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

        var graph = new InterferenceGraph(method, analyzer);
        graph.buildGraph();

        assertTrue("a",graph.getNeighbors("i").contains("tmp"));
    }

    @Test
    public void testDeadVariableNoInterference() {
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

        var graph = new InterferenceGraph(method, analyzer);
        graph.buildGraph();

        assertTrue("a and b MUST interfere due to dead var declaration", graph.getNeighbors("a").contains("b"));
    }

    @Test
    public void testMultipleInterference() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                int c;
                int d;
                a = 1;
                b = 2;
                c = a + b;
                d = c + b;
                return d;
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

        var graph = new InterferenceGraph(method, analyzer);
        graph.buildGraph();

        assertTrue("a",graph.getNeighbors("b").contains("a"));
        assertTrue("a",graph.getNeighbors("b").contains("c"));
    }
}
