package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class Assignments extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(JmmKind.VAR_DECL, this::visitVarDecl);
        addVisit(JmmKind.FOR_STMT, this::visitForStmt);
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        var types = TypeUtils.with(table);
        var lhs = assignStmt.getChild(0);

        if (!isAssignableTarget(lhs, types)) {
            addReport(newError(lhs, "Left-hand side of assignment is not assignable"));
            return null;
        }

        var lType = types.getExprType(lhs);
        var rType = types.getExprType(assignStmt.getChild(1));

        validateAssignment(assignStmt, lType, rType, types);
        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {



        var types = TypeUtils.with(table);

        var lType = types.getDeclaredType(varDecl);

        // Rearranged to allow for array validation
        if (varDecl.getChildren().size() <= 1) {
            return null;
        }

        var rType = types.getExprType(varDecl.getChild(1));

        if (types.isAssignable(rType, lType)) {
            return null;
        }

        var message = "Cannot initialize variable of type '" + lType.print() +
                "' with expression of type '" + rType.print() + "'";
        addReport(newError(varDecl, message));

        return null;
    }

    private Void visitForStmt(JmmNode forStmt, SymbolTable table) {
        var types = TypeUtils.with(table);
        int childIndex = 0;

        if (forStmt.getOptional(JmmAttributes.FOR_STMT.INIT_VAR).isPresent()) {
            var initVar = forStmt.get(JmmAttributes.FOR_STMT.INIT_VAR);
            var initExpr = forStmt.getChild(childIndex++);
            validateForAssignment(forStmt, initVar, initExpr, types);
        }

        if (childIndex < forStmt.getNumChildren() && forStmt.getChild(childIndex).isInstance(JmmKind.FOR_CONDITION)) {
            childIndex++;
        }

        var hasAssignmentUpdate = forStmt.getOptional(JmmAttributes.FOR_STMT.UPDATE_VAR).isPresent()
                && forStmt.getOptional(JmmAttributes.FOR_STMT.OP).isEmpty();

        if (hasAssignmentUpdate && childIndex < forStmt.getNumChildren()) {
            var updateVar = forStmt.get(JmmAttributes.FOR_STMT.UPDATE_VAR);
            var updateExpr = forStmt.getChild(childIndex);
            validateForAssignment(forStmt, updateVar, updateExpr, types);
        }

        return null;
    }

    private void validateForAssignment(JmmNode forStmt, String varName, JmmNode expr, TypeUtils types) {
        var lType = types.getVariableType(varName, forStmt);

        if (lType.isEmpty()) {
            return;
        }

        var rType = types.getExprType(expr);
        validateAssignment(forStmt, lType.get(), rType, types);
    }

    private void validateAssignment(JmmNode reportNode, JmmType lType, JmmType rType, TypeUtils types) {
        if (types.isAssignable(rType, lType)) {
            return;
        }

        var message = "Cannot assign expression of type '" + rType.print() +
                "' to assignee of type '" + lType.print() + "'";
        addReport(newError(reportNode, message));
    }

    private boolean isAssignableTarget(JmmNode lhs, TypeUtils types) {
        if (lhs.isInstance(JmmKind.PARENTHESES_EXPR)) {
            return isAssignableTarget(lhs.getChild(0), types);
        }

        if (lhs.isInstance(JmmKind.VAR_REF_EXPR) || lhs.isInstance(JmmKind.ARRAY_ACCESS)) {
            return true;
        }

        if (lhs.isInstance(JmmKind.FIELD_ACCESS)) {
            var fieldName = lhs.get("name");
            var receiverType = types.getExprType(lhs.getChild(0));
            return !("length".equals(fieldName) && receiverType.isArray());
        }

        return false;
    }
}
