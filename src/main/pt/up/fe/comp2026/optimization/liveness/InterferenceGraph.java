package pt.up.fe.comp2026.optimization.liveness;
import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.Node;
import org.specs.comp.ollir.inst.Instruction;
import java.util.*;

public class InterferenceGraph {
    private final LivenessAnalyzer livenessAnalyzer;
    private final Method method;
    private final Map<String, Set<String>> graph = new HashMap<>();

    public InterferenceGraph(Method method, LivenessAnalyzer livenessAnalyzer) {
        this.method = method;
        this.livenessAnalyzer = livenessAnalyzer;
    }
    public void buildGraph() {
        for (Instruction inst : method.getInstructions()) {
            Set<String> defVars = livenessAnalyzer.getDef(inst);
            Set<String> outVars = livenessAnalyzer.getOut(inst);
            Set<String> interferingVars = new HashSet<>();
            interferingVars.addAll(defVars);
            interferingVars.addAll(outVars);

            for (String var : interferingVars) {
                graph.putIfAbsent(var, new HashSet<>());
            }

            List<String> vars = new ArrayList<>(interferingVars);

            for (int i = 0; i < vars.size(); i++) {
                for (int j = i + 1; j < vars.size(); j++) {
                    String v1 = vars.get(i);
                    String v2 = vars.get(j);
                    graph.get(v1).add(v2);
                    graph.get(v2).add(v1);
                }
            }
        }
    }
    public Set<String> getNodes() {
        return graph.keySet();
    }
    public Set<String> getNeighbors(String node) {
        return graph.getOrDefault(node, Collections.emptySet());
    }
    public Map<String, Set<String>> getGraph() {
        return graph;
    }
}
