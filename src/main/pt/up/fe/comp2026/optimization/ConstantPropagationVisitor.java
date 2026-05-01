package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

import java.util.HashMap;
import java.util.Map;

public class ConstantPropagationVisitor extends AJmmVisitor<SymbolTable, Void> {

    private Map<String, JmmNode> constantMap = new HashMap<>();
    private boolean modified = false;

    @Override
    protected void buildVisitor() {
        addVisit(JmmKind.ASSIGN_STMT, this::handleAssign);
        addVisit(JmmKind.VAR_REF_EXPR, this::handleVarRef);
        addVisit(JmmKind.METHOD_DECL, this::newMethod);
        addVisit(JmmKind.IF_ELSE_STMT, this::handleIf);
        addVisit(JmmKind.WHILE_STMT, this::handleWhile);
        setDefaultVisit(this::defaultVisit);
    }

    private Void defaultVisit(JmmNode node, SymbolTable table) {
        for (var child : node.getChildren()) {
            visit(child, table);
        }
        return null;
    }

    private Void handleAssign(JmmNode node, SymbolTable table) {
        visit(node.getChild(1), table);

        var leftSide = node.getChild(0);
        var rightSide = node.getChild(1);

        if (leftSide.isInstance(JmmKind.VAR_REF_EXPR)) {
            String varName = leftSide.get("name");
            if (isLiteral(rightSide)) {
                constantMap.put(varName, rightSide);
            } else {
                constantMap.remove(varName);
            }
        }
        return null;
    }

    private Void handleVarRef(JmmNode node, SymbolTable table) {
        var parent = node.getParent();
        if (parent != null && parent.isInstance(JmmKind.ASSIGN_STMT)
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

    private Void newMethod(JmmNode node, SymbolTable table) {
        constantMap.clear();
        defaultVisit(node, table);
        return null;
    }

    private Void handleIf(JmmNode node, SymbolTable table) {
        visit(node.getChild(0), table);

        Map<String, JmmNode> snapshotMap = new HashMap<>(constantMap);

        visit(node.getChild(1), table);

        constantMap = new HashMap<>(snapshotMap);
        if (node.getNumChildren() > 2) {
            visit(node.getChild(2), table);
        }

        constantMap = new HashMap<>(snapshotMap);
        invalidateAssignedVars(node.getChild(1));
        if (node.getNumChildren() > 2) {
            invalidateAssignedVars(node.getChild(2));
        }

        return null;
    }

    private Void handleWhile(JmmNode node, SymbolTable table) {
        invalidateAssignedVars(node.getChild(1));

        // Visit condition
        visit(node.getChild(0), table);

        // Visit body
        visit(node.getChild(1), table);

        return null;
    }

    private void invalidateAssignedVars(JmmNode block) {
        for (JmmNode assign : block.getDescendants(JmmKind.ASSIGN_STMT)) {
            if (assign.getChild(0).isInstance(JmmKind.VAR_REF_EXPR)) {
                constantMap.remove(assign.getChild(0).get("name"));
            }
        }
    }

    private boolean isLiteral(JmmNode node) {
        return node.isInstance(JmmKind.INTEGER_LITERAL) || node.isInstance(JmmKind.BOOLEAN_LITERAL);
    }

    public boolean isModified() {
        return modified;
    }
}