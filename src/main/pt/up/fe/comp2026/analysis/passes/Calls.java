package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class Calls extends AnalysisVisitor {
    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_CALL, this::visitCall);
        addVisit(JmmKind.IMPLICIT_CALL, this::visitCall);
    }

    private Void visitCall(JmmNode callExpr, SymbolTable symbolTable) {
        var typeUtils = new TypeUtils(symbolTable);

        if (callExpr.isInstance(JmmKind.METHOD_CALL)) {
            var recvType = getReceiverType(callExpr, typeUtils);
            if (recvType == null) {
                return null;
            }

            if (!recvType.isClass()) {
                addReport(newError(callExpr, "Method call receiver is not a class type: " + recvType));
                return null;
            }
        }

        try {
            typeUtils.getExprType(callExpr);
        } catch (RuntimeException e) {
            addReport(newError(callExpr, "Method call failed: " + e.getMessage()));
        }

        return null;
    }

    private JmmType getReceiverType(JmmNode callExpr, TypeUtils typeUtils) {
        try {
            return typeUtils.getExprType(callExpr.getChild(0));
        } catch (RuntimeException e) {
            addReport(newError(callExpr, "Method call failed: " + e.getMessage()));
            return null;
        }
    }
}
