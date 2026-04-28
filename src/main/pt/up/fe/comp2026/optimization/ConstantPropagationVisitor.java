package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

import java.util.HashMap;
import java.util.Map;

public class ConstantPropagationVisitor extends PreorderJmmVisitor<SymbolTable, Void> {

    private Map<String, JmmNode> constantMap = new HashMap<>();
    private boolean modified = false;

    @Override
    protected void buildVisitor() {
        addVisit(JmmKind.ASSIGN_STMT, this::handleAssign);
        addVisit(JmmKind.VAR_REF_EXPR, this::handleVarRef);
        addVisit(JmmKind.METHOD_DECL, this::newMethod);
        setDefaultVisit((node, table) -> null);
    }

    private Void handleAssign(JmmNode node, SymbolTable table) {
        var leftSide = node.getChild(0);
        var rightSide = node.getChild(1);

        if (!leftSide.getKind().equals("VarRefExpr")) {
            return null;
        }

        String varName = leftSide.get("name");

        if (isLiteral(rightSide)) {
            constantMap.put(varName, rightSide);
        } else {
            constantMap.remove(varName);
        }

        return null;
    }


    private Void handleVarRef(JmmNode node, SymbolTable table) {
        // Skip if this node is the LHS of an assignment
        var parent = node.getParent();
        if (parent != null && parent.getKind().equals(JmmKind.ASSIGN_STMT.toString())
                && parent.getChild(0) == node) {
            return null;
        }

        String varName = node.get("name");
        if (constantMap.containsKey(varName)) {
            JmmNode constantNode = constantMap.get(varName);
            JmmNode newNode = new JmmNodeImpl(constantNode.getKind());
            newNode.put("value", constantNode.get("value"));
            node.replace(newNode);
            this.modified = true;
        }
        return null;
    }

    private Void newMethod (JmmNode node, SymbolTable table) {
        constantMap.clear();
        return null;
    }

    private boolean isLiteral(JmmNode node) {
        String kind = String.valueOf(node.getKind());
        return kind.equals("IntegerLiteral") || kind.equals("BooleanLiteral");
    }

    public boolean isModified() {
        return modified;
    }
}
