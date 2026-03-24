package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class Assignments extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(JmmKind.VAR_DECL, this::visitVarDecl);
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        var types = TypeUtils.with(table);

        var lType = types.getExprType(assignStmt.getChild(0));
        var rType = types.getExprType(assignStmt.getChild(1));

        if (types.isAssignable(rType, lType)) {
            return null;
        }

        var message = "Cannot assign expression of type '" + rType.print() +
                "' to assignee of type '" + lType.print() + "'";
        addReport(newError(assignStmt, message));
        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {



        var types = TypeUtils.with(table);

        var lType = types.getDeclaredType(varDecl);

        if (lType.isArray() && lType.print().contains("void")) {
            addReport(newError(varDecl, "Arrays cannot have 'void' as a base type."));
            return null;
        }

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
}
