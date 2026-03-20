package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;

public class IdentifierResolution extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(JmmKind.ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);
        addVisit(JmmKind.FOR_STMT, this::visitForStmt);
        addVisit(JmmKind.FIELD_ACCESS, this::visitFieldAccess);
    }

    private Void visitVarRefExpr(JmmNode node, SymbolTable table) {
        checkIdentifierExists(node.get("name"), node, table);
        return null;
    }

    private Void visitArrayAssignStmt(JmmNode node, SymbolTable table) {
        checkIdentifierExists(node.get("var"), node, table);
        return null;
    }

    private Void visitForStmt(JmmNode node, SymbolTable table) {
        node.getOptional("initVar")
                .ifPresent(varName -> checkIdentifierExists(varName, node, table));

        node.getOptional("updateVar")
                .ifPresent(varName -> checkIdentifierExists(varName, node, table));

        return null;
    }

    private Void visitFieldAccess(JmmNode node, SymbolTable table) {
        var receiver = node.getChild(0);
        var fieldName = node.get("name");

        if (isCurrentClassInstance(receiver, table) && table.getField(fieldName).isEmpty()) {
            addReport(newError(node, "Field '" + fieldName + "' does not exist."));
        }

        return null;
    }

    private void checkIdentifierExists(String idName, JmmNode node, SymbolTable table) {
        var methodNode = node.getAncestor(JmmKind.METHOD_DECL);
        var isStaticMethod = methodNode
                .map(this::isStaticMethod)
                .orElse(false);

        var isLocalOrParam = methodNode
                .map(method -> isLocalOrParam(idName, method))
                .orElse(false);

        var isField = !isStaticMethod && table.getField(idName).isPresent();
        var isCurrentClass = idName.equals(table.getClassName()) || idName.equals(table.getFullyQualifiedName());
        var isImportedClass = isImportedClass(idName, table);

        if (!isLocalOrParam && !isField && !isCurrentClass && !isImportedClass) {
            addReport(newError(node, "Variable '" + idName + "' does not exist."));
        }
    }

    private boolean isStaticMethod(JmmNode methodNode) {
        return methodNode.getBoolean(JmmAttributes.METHOD_DECL.IS_STATIC, false);
    }

    private boolean isLocalOrParam(String idName, JmmNode methodNode) {
        if (methodNode.isInstance(JmmKind.MAIN_METHOD_DECL) && idName.equals(methodNode.get("args"))) {
            return true;
        }

        var isParam = methodNode.getChildren(JmmKind.PARAM).stream()
                .anyMatch(param -> idName.equals(param.get("name")));

        if (isParam) {
            return true;
        }

        return methodNode.getChildren(JmmKind.VAR_DECL).stream()
                .anyMatch(local -> idName.equals(local.get("name")));
    }

    private boolean isImportedClass(String idName, SymbolTable table) {
        if (!(table instanceof JmmSymbolTable jmmTable)) {
            return false;
        }

        return jmmTable.getImportedFullyQualifiedName(idName).isPresent() || jmmTable.isImplicitImport(idName);
    }

    private boolean isCurrentClassInstance(JmmNode receiver, SymbolTable table) {
        try {
            JmmType receiverType = TypeUtils.with(table).getExprType(receiver);

            return receiverType.isClass()
                    && !receiverType.asClass().staticRef()
                    && receiverType.asClass().fullyQualifiedName().equals(table.getFullyQualifiedName());
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
