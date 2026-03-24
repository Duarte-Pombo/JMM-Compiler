package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class This extends AnalysisVisitor {
    private JmmNode currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.THIS, this::visitThis);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable symbolTable) {
        currentMethod = method;
        return null;
    }

    private Void visitThis(JmmNode thisNode, SymbolTable symbolTable) {
        if (currentMethod == null) {
            validateTypedUsage(thisNode, symbolTable);
            return null;
        }

        var isStaticMethod = currentMethod.getBoolean(JmmAttributes.METHOD_DECL.IS_STATIC, false);
        if (isStaticMethod) {
            addReport(newError(thisNode, "'this' cannot be used in a static function"));

            return null;
        }

        validateTypedUsage(thisNode, symbolTable);

        return null;
    }

    private void validateTypedUsage(JmmNode thisNode, SymbolTable symbolTable) {
        var parent = thisNode.getParent();
        if (parent == null) {
            return;
        }

        var types = TypeUtils.with(symbolTable);

        if (parent.isInstance(JmmKind.ASSIGN_STMT) && thisNode.getIndexOfSelf() == 0) {
            addReport(newError(thisNode, "'this' cannot be assigned"));
            return;
        }

        var expectedType = types.getExpectedType(thisNode, currentMethod);
        if (expectedType.isEmpty() || types.isAssignable(types.getExprType(thisNode), expectedType.get())) {
            return;
        }

        addReport(newError(thisNode, "'this' is not compatible with type '" + expectedType.get().print() + "'"));
    }
}
