package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;

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
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(thisNode),
                    NodeUtils.getColumn(thisNode),
                    "'this' cannot be used in a static function",
                    null));

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

        if (parent.isInstance(JmmKind.ASSIGN_STMT) && thisNode.getIndexOfSelf() == 0) {
            addReport(newError(thisNode, "'this' cannot be assigned"));
            return;
        }

        var expectedType = getExpectedType(thisNode, symbolTable);
        if (expectedType == null || isCompatibleWithThis(expectedType, symbolTable)) {
            return;
        }

        addReport(newError(thisNode, "'this' is not compatible with type '" + expectedType.print() + "'"));
    }

    private JmmType getExpectedType(JmmNode thisNode, SymbolTable symbolTable) {
        var parent = thisNode.getParent();
        if (parent == null) {
            return null;
        }

        var types = TypeUtils.with(symbolTable);

        if (parent.isInstance(JmmKind.ASSIGN_STMT)) {
            return thisNode.getIndexOfSelf() == 1 ? types.getExprType(parent.getChild(0)) : null;
        }

        if (parent.isInstance(JmmKind.RETURN_STMT)) {
            return thisNode.getIndexOfSelf() == 0 ? getCurrentMethodReturnType(symbolTable) : null;
        }

        if (parent.isInstance(JmmKind.VAR_DECL)) {
            return thisNode.getIndexOfSelf() == 1
                    ? TypeUtils.convertType(parent.getChild(0), symbolTable.getImports(), symbolTable.getFullyQualifiedName())
                    : null;
        }

        return null;
    }

    private JmmType getCurrentMethodReturnType(SymbolTable symbolTable) {
        if (currentMethod == null) {
            return null;
        }

        var signature = TypeUtils.with(symbolTable).getMethodDeclSignature(currentMethod);
        return symbolTable.getMethod(signature)
                .map(MethodSymbol::returnType)
                .orElse(null);
    }

    private boolean isCompatibleWithThis(JmmType expectedType, SymbolTable symbolTable) {
        if (!expectedType.isClass()) {
            return false;
        }

        if (expectedType.asClass().staticRef()) {
            return false;
        }

        return isCurrentClassOrSuperType(symbolTable, expectedType.asClass().fullyQualifiedName());
    }

    private boolean isCurrentClassOrSuperType(SymbolTable symbolTable, String expectedClassName) {
        if (sameClass(expectedClassName, symbolTable.getFullyQualifiedName())) {
            return true;
        }

        var superClassName = symbolTable.getSuperFullyQualifiedName();
        if (superClassName == null) {
            return false;
        }

        if (sameClass(expectedClassName, superClassName)) {
            return true;
        }

        if (sameClass(superClassName, "Object") || sameClass(superClassName, "java.lang.Object")) {
            return false;
        }

        if (!(symbolTable instanceof JmmSymbolTable jmmSymbolTable)) {
            return false;
        }

        return jmmSymbolTable.getImportedSymbolTable(superClassName)
                .map(importedTable -> isCurrentClassOrSuperType(importedTable, expectedClassName))
                .orElse(false);
    }

    private boolean sameClass(String left, String right) {
        if (left == null || right == null) {
            return false;
        }

        return left.equals(right) || simpleName(left).equals(simpleName(right));
    }

    private String simpleName(String className) {
        var lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }
}
