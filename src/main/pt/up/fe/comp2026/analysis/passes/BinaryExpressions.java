package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class BinaryExpressions extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit(JmmKind.BINARY_EXPR, this::visitBinExpr);
        addVisit(JmmKind.UNARY_EXPR, this::visitUnaryExpr);
        addVisit(JmmKind.NEGATION_EXPR, this::visitNegationExpr);
    }

    private Void visitBinExpr(JmmNode binExpr, SymbolTable table) {
        var typeUtils = TypeUtils.with(table);

        var left = binExpr.getChild(0);
        var right = binExpr.getChild(1);

        var typeLeft = typeUtils.getExprType(left);
        var typeRight = typeUtils.getExprType(right);

        var op = binExpr.get("op");

        switch (op) {
            case "+", "-", "*", "/", "%", "<", ">", "<=", ">=":
                if (typeLeft.isArray() || typeRight.isArray()) break;
                if (typeLeft.equals(TypeUtils.intType()) && typeRight.equals(TypeUtils.intType())) return null;
                break;
            case "&&", "||":
                if (typeLeft.isArray() || typeRight.isArray()) break;
                if (typeLeft.equals(TypeUtils.booleanType()) && typeRight.equals(TypeUtils.booleanType())) return null;
                break;

            case "==", "!=":
                if (typeUtils.isAssignable(typeLeft,typeRight) || typeUtils.isAssignable(typeRight,typeLeft)) return null;
                break;
        }

        addReport(newError(binExpr, "Invalid binary expression"));
        return null;
    }

    private Void visitUnaryExpr(JmmNode unaryExpr, SymbolTable table) {
        var typeUtils = TypeUtils.with(table);

        var value = unaryExpr.getChild(0);
        var typeValue = typeUtils.getExprType(value);

        if (typeValue.equals(TypeUtils.intType())) {
            return null;
        }

        addReport(newError(unaryExpr, "Invalid unary expression"));
        return null;
    }

    private Void visitNegationExpr(JmmNode negationExpr, SymbolTable table) {
        var typeUtils = TypeUtils.with(table);

        var value = negationExpr.getChild(0);
        var typeValue = typeUtils.getExprType(value);

        if (typeValue.equals(TypeUtils.booleanType()) && !typeValue.isArray()) {
            return null;
        }

        addReport(newError(negationExpr, "Invalid unary expression"));
        return null;
    }
}
