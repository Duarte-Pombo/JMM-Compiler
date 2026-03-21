package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
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

        try {
            typeUtils.getExprType(callExpr);
        } catch (RuntimeException e) {
            addReport(newError(callExpr, "Method call failed: " + e.getMessage()));
        }

        return null;
    }
}
