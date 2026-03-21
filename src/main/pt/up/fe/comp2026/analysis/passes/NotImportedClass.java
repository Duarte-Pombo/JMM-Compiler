package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitorWithTable;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class NotImportedClass extends AnalysisVisitorWithTable {

    public NotImportedClass(SymbolTable table) {
        super(table);
    }

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.VAR_DECL, this::visitVarDecl);
        addVisit(JmmKind.NEW_OBJECT, this::visitNewObject);
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable symbolTable) {
        validateTypeNode(methodDecl.getChild(0), methodDecl, "Method return type");

        for (var param : methodDecl.getChildren(JmmKind.PARAM)) {
            validateTypeNode(param.getChild(0), param, "Parameter type");
        }

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable symbolTable) {
        validateTypeNode(varDecl.getChild(0), varDecl, "Variable type");
        return null;
    }

    private Void visitNewObject(JmmNode newObjectExpr, SymbolTable symbolTable) {
        var className = newObjectExpr.get("name");

        if (!isAvailableClassName(className)) {
            addReport(newError(newObjectExpr, "Class '" + className + "' is not imported"));
        }

        return null;
    }

    private void validateTypeNode(JmmNode typeNode, JmmNode reportNode, String label) {
        var type = types.convertType(typeNode, table.getImports(), table.getFullyQualifiedName());

        if (isAvailableType(type)) {
            return;
        }

        addReport(newError(reportNode, label + " '" + type.print() + "' is not imported"));
    }

    private boolean isAvailableType(JmmType type) {
        if (type.isPrimitive() || type.equals(pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType.VOID)) {
            return true;
        }

        if (type.isArray()) {
            var arrayType = (JmmArrayType) type;
            return isAvailableType(arrayType.itemType());
        }

        if (!type.isClass()) {
            return false;
        }

        return isAvailableClassName(type.asClass().fullyQualifiedName());
    }

    private boolean isAvailableClassName(String className) {
        return sameClass(className, table.getClassName())
                || sameClass(className, table.getFullyQualifiedName())
                || table.getImportedFullyQualifiedName(className).isPresent()
                || table.isImplicitImport(className);
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
