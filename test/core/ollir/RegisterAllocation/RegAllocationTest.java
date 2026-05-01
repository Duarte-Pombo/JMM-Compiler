package core.ollir.RegisterAllocation;

import org.junit.Test;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.test.env.JmmTestEnv;
import pt.up.fe.comp2026.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2026.optimization.JmmOptimizationImpl;
import pt.up.fe.comp2026.optimization.RegisterAllocation.LivenessAnalyzer;
import pt.up.fe.comp2026.optimization.RegisterAllocation.InterferenceGraph;

public class RegAllocationTest extends JmmTestEnv {

    public RegAllocationTest() {
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

    private InterferenceGraph buildGraph(String code) {
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

        return graph;
    }

    @Test
    public void testRegAllocNoChange2() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                a = 0;
                b = 2;
                return a + b;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("a and b interfere", graph.getNeighbors("a").contains("b"));
    }

    @Test
    public void testRegAllocMixedVars1() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                int c;
                int d;
                a = 0;
                b = a;
                c = b;
                d = c;
                return d;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("a and b do not interfere", !graph.getNeighbors("a").contains("b"));
        assertTrue("b and c do not interfere", !graph.getNeighbors("b").contains("c"));
    }

    @Test
    public void testRegAllocMixedVars2() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                int c;
                int d;
                a = 0;
                b = 2;
                c = a;
                d = b + c;
                return d;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("b and c interfere", graph.getNeighbors("b").contains("c"));
        assertTrue("a and b interfere", graph.getNeighbors("a").contains("b"));
    }

    @Test
    public void testRegAllocMixedVars3() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                int c;
                int d;
                a = 0;
                b = 2;
                c = b;
                d = a + c;
                return d + b;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("b and d interfere", graph.getNeighbors("b").contains("d"));
        assertTrue("a and c interfere", graph.getNeighbors("a").contains("c"));
    }

    @Test
    public void testRegAllocParamAndVars1() {
        var code = """
        package x;
        class A {
            public int method(int arg) {
                int a;
                int b;
                int c;
                int d;
                a = arg;
                b = a;
                c = b;
                d = c;
                return d;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("arg has no neighbors", graph.getNeighbors("arg").isEmpty());
        assertTrue("a and b do not interfere", !graph.getNeighbors("a").contains("b"));
    }

    @Test
    public void testRegAllocIf2() {
        var code = """
    package x;
    class A {
        public int method(int initB, int initC, int initE, int initF) {
            int a;
            int b;
            int c;
            int d;
            int i;
            b = initB;
            c = initC;
            a = b + c;
            d = initE + initF;
            d = d + initE;
            if (a == 0) {
                b = a - d;
            } else {
                b = a + d;
            }
            i = b;
            return i;
        }
    }""";

        var graph = buildGraph(code);

        assertTrue("a and d interfere", graph.getNeighbors("a").contains("d"));
        assertTrue("b and c interfere", graph.getNeighbors("b").contains("c"));
    }

    @Test
    public void testRegAllocIf3() {
        var code = """
    package x;
    class A {
        public int method(int initB, int initC, int initE, int initF) {
            int a;
            int b;
            int c;
            int d;
            int e;
            int f;
            int i;
            b = initB;
            c = initC;
            a = b + c;
            e = initE;
            f = initF;
            d = e + f;
            d = d + e;
            if (a == 0) {
                b = a - d;
            } else {
                b = a + d;
            }
            i = b;
            return i;
        }
    }""";

        var graph = buildGraph(code);

        assertTrue("a and d interfere", graph.getNeighbors("a").contains("d"));
        assertTrue("d and e interfere", graph.getNeighbors("d").contains("e"));
        assertTrue("e and f interfere", graph.getNeighbors("e").contains("f"));
    }

    @Test
    public void testRegAllocIf4() {
        var code = """
    package x;
    class A {
        public int method(int initB, int initC, int initE, int initF) {
            int a;
            int b;
            int c;
            int d;
            int e;
            int f;
            int i;
            b = initB;
            c = initC;
            e = initE;
            f = initF;
            a = b + c;
            d = e + f;
            d = d + e;
            if (a == 0) {
                b = a - d;
            } else {
                b = a + d;
            }
            i = b;
            return i;
        }
    }""";

        var graph = buildGraph(code);

        assertTrue("a and d interfere", graph.getNeighbors("a").contains("d"));
        assertTrue("b and c interfere", graph.getNeighbors("b").contains("c"));
        assertTrue("e and f interfere", graph.getNeighbors("e").contains("f"));
    }

    @Test
    public void testInterferenceWhileLoop() {
        var code = """
        package x;
        class A {
            public int method() {
                int x;
                int y;
                int z;
                x = 0;
                y = 10;
                while (x < 5) {
                    z = x + y;
                    x = x + 1;
                }
                return z;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("x and y interfere", graph.getNeighbors("x").contains("y"));
        assertTrue("x and z interfere", graph.getNeighbors("x").contains("z"));
    }

    @Test
    public void testNoInterferenceSequential() {
        var code = """
        package x;
        class A {
            public int method() {
                int p;
                int q;
                int r;
                p = 3;
                q = p;
                r = q;
                return r;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("p and q do not interfere", !graph.getNeighbors("p").contains("q"));
        assertTrue("q and r do not interfere", !graph.getNeighbors("q").contains("r"));
    }


    @Test
    public void testInterferenceWithConstants() {
        var code = """
        package x;
        class A {
            public int method() {
                int m;
                int n;
                int o;
                m = 7;
                n = 8;
                o = m + n;
                return o;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("m and n interfere", graph.getNeighbors("m").contains("n"));
    }

    @Test
    public void testNoInterferenceDeadCode() {
        var code = """
        package x;
        class A {
            public int method() {
                int temp;
                int finalVal;
                temp = 5;
                finalVal = 10;
                return finalVal;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("temp and finalVal do not interfere", !graph.getNeighbors("temp").contains("finalVal"));
    }

    @Test
    public void testInterferenceBasic() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                int c;
                a = 5;
                b = 10;
                c = a + b;
                return c;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("a and b interfere", graph.getNeighbors("a").contains("b"));
    }

    @Test
    public void testInterferenceSimple() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                a = 1;
                b = 3;
                return a + b;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("a and b interfere", graph.getNeighbors("a").contains("b"));
    }

    @Test
    public void testInterferenceSimpleAlt() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                a = 0;
                b = 2;
                return a + b;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("a and b interfere", graph.getNeighbors("a").contains("b"));
    }

    @Test
    public void testNoInterferenceChainAlt() {
        var code = """
        package x;
        class A {
            public int method() {
                int a;
                int b;
                int c;
                int d;
                a = 0;
                b = a;
                c = b;
                d = c;
                return d;
            }
        }""";

        var graph = buildGraph(code);

        assertTrue("a and b do not interfere", !graph.getNeighbors("a").contains("b"));
        assertTrue("b and c do not interfere", !graph.getNeighbors("b").contains("c"));
    }
}
