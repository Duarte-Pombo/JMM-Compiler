package pt.up.fe.comp2026.optimization.RegisterAllocation;

import org.specs.comp.ollir.Method;

import java.util.*;

public class GraphColoring {
    private final InterferenceGraph graph;
    private final Method method;
    private final int k;
    private final Map<String, Integer> coloring;

    public GraphColoring(Method method, InterferenceGraph graph, int k) {
        this.method = method;
        this.graph = graph;
        this.k = k;
        this.coloring = new HashMap<>();
    }

    public boolean colorGraph() {
        Map<String, Set<String>> workingGraph = new HashMap<>();
        for (String node : graph.getNodes()) {
            workingGraph.put(node, new HashSet<>(graph.getNeighbors(node)));
        }

        Set<String> precolored = new HashSet<>();
        for (org.specs.comp.ollir.Element param : method.getParams()) {
            precolored.add(((org.specs.comp.ollir.Operand) param).getName());
        }
        if (!method.isStaticMethod()) {
            precolored.add("this");
        }

        for (String pc : precolored) {
            if (workingGraph.containsKey(pc)) {
                workingGraph.remove(pc);
                for (Set<String> neighbors : workingGraph.values()) {
                    neighbors.remove(pc);
                }
            }
        }

        Stack<String> stack = new Stack<>();
        while (!workingGraph.isEmpty()) {
            String toRemove = null;
            for (Map.Entry<String, Set<String>> entry : workingGraph.entrySet()) {
                if (entry.getValue().size() < k) {
                    toRemove = entry.getKey();
                    break;
                }
            }

            if (toRemove == null) {
                return false;
            }

            stack.push(toRemove);
            workingGraph.remove(toRemove);
            for (Set<String> neighbors : workingGraph.values()) {
                neighbors.remove(toRemove);
            }
        }

        int startReg = method.isStaticMethod() ? 0 : 1;
        startReg += method.getParams().size();

        while (!stack.isEmpty()) {
            String node = stack.pop();
            Set<Integer> usedColors = new HashSet<>();

            for (String neighbor : graph.getNeighbors(node)) {
                if (coloring.containsKey(neighbor)) {
                    usedColors.add(coloring.get(neighbor));
                } else if (precolored.contains(neighbor)) {
                    var desc = method.getVarTable().get(neighbor);
                    if (desc != null) {
                        usedColors.add(desc.getVirtualReg());
                    }
                }
            }

            int color = startReg;
            while (usedColors.contains(color)) {
                color++;
            }
            if (k > 0 && color >= k) {
               return false;
            }
            coloring.put(node, color);
        }

        for (Map.Entry<String, Integer> entry : coloring.entrySet()) {
            var desc = method.getVarTable().get(entry.getKey());
            if (desc != null) {
                desc.setVirtualReg(entry.getValue());
            }
        }
        return true;
    }
}
