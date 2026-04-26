package pt.up.fe.comp2026.optimization.liveness;
import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import java.util.*;

public class LivenessAnalyzer {
    private final Method method;
    private final Map<Node, Set<String>> defs = new HashMap<>();
    private final Map<Node, Set<String>> uses = new HashMap<>();
    private final Map<Node, Set<String>> in = new HashMap<>();
    private final Map<Node, Set<String>> out = new HashMap<>();
    public LivenessAnalyzer(Method method) {
        this.method = method;
        initSets();
    }
    private void initSets() {
        for (Instruction inst : method.getInstructions()) {
            defs.put(inst, new HashSet<>());
            uses.put(inst, new HashSet<>());
            in.put(inst, new HashSet<>());
            out.put(inst, new HashSet<>());
        }
    }
    public void computeDefUse() {
        for (Instruction inst : method.getInstructions()) {
            computeNodeDefUse(inst);
        }
    }

    public void computeInOut() {
        boolean changed = true;

        List<Instruction> instructions = method.getInstructions();
        List<Instruction> reversedInstructions = new ArrayList<>(instructions);
        Collections.reverse(reversedInstructions);

        while (changed) {
            changed = false;

            for (Instruction inst : reversedInstructions) {
                Set<String> oldIn = in.get(inst);
                Set<String> oldOut = out.get(inst);

                Set<String> newOut = new HashSet<>();
                for (Node succ : inst.getSuccessors()) {
                    if (in.containsKey(succ)) {
                        newOut.addAll(in.get(succ));
                    }
                }

                Set<String> newIn = new HashSet<>(newOut);
                newIn.removeAll(defs.get(inst));
                newIn.addAll(uses.get(inst));

                if (!newIn.equals(oldIn) || !newOut.equals(oldOut)) {
                    in.put(inst, newIn);
                    out.put(inst, newOut);
                    changed = true;
                }
            }
        }
    }

    private void computeNodeDefUse(Instruction inst) {
        Set<String> nodeDef = defs.get(inst);
        Set<String> nodeUse = uses.get(inst);
        if (inst instanceof AssignInstruction assignInst) {
            if (assignInst.getDest() instanceof ArrayOperand arrOp) {
                nodeUse.add(arrOp.getName());
                for (Element idx : arrOp.getIndexOperands()) {
                    extractUses(idx, nodeUse);
                }
            } else if (assignInst.getDest() instanceof Operand op) {
                nodeDef.add(op.getName());
            }
            extractUsesInst(assignInst.getRhs(), nodeUse);
        } else {
            extractUsesInst(inst, nodeUse);
        }
    }
    private void extractUsesInst(Instruction inst, Set<String> nodeUse) {
        if (inst == null) return;
        if (inst instanceof CallInstruction callInst) {
            extractUses(callInst.getCaller(), nodeUse);
            if (callInst.getArguments() != null) {
                for (Element el : callInst.getArguments()) {
                    extractUses(el, nodeUse);
                }
            }
        } else if (inst instanceof ReturnInstruction returnInst) {
            if (returnInst.hasReturnValue()) {
                extractUses(returnInst.getOperand().orElse(null), nodeUse);
            }
        } else if (inst instanceof CondBranchInstruction branchInst) {
            for (Element el : branchInst.getOperands()) {
                extractUses(el, nodeUse);
            }
            extractUsesInst(branchInst.getCondition(), nodeUse);
        } else if (inst instanceof OpInstruction opInst) {
            for (Element el : opInst.getOperands()) {
                extractUses(el, nodeUse);
            }
        } else if (inst instanceof SingleOpInstruction singleOpInst) {
            extractUses(singleOpInst.getSingleOperand(), nodeUse);
        } else if (inst instanceof FieldInstruction fieldInst) {
            extractUses(fieldInst.getObject(), nodeUse);
            extractUses(fieldInst.getField(), nodeUse);
            if (fieldInst instanceof PutFieldInstruction putFieldInst) {
                extractUses(putFieldInst.getValue(), nodeUse);
            }
        } else if (inst instanceof AssignInstruction assignInst) {
            extractUsesInst(assignInst.getRhs(), nodeUse);
        }
    }
    private void extractUses(Element el, Set<String> nodeUse) {
        if (el == null) return;
        if (el instanceof Operand op) {
            if (!op.getName().equals("this")) {
                nodeUse.add(op.getName());
            }
        } else if (el instanceof ArrayOperand arrOp) {
            if (!arrOp.getName().equals("this")) {
                nodeUse.add(arrOp.getName());
            }
            for (Element idx : arrOp.getIndexOperands()) {
                extractUses(idx, nodeUse);
            }
        }
    }
    public Set<String> getDef(Node node) { return defs.getOrDefault(node, Collections.emptySet()); }
    public Set<String> getUse(Node node) { return uses.getOrDefault(node, Collections.emptySet()); }
    public Set<String> getIn(Node node) { return in.getOrDefault(node, Collections.emptySet()); }
    public Set<String> getOut(Node node) { return out.getOrDefault(node, Collections.emptySet()); }
}
