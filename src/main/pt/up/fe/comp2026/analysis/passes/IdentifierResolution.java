package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
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

        node.getOptional("updateVar").ifPresent(varName -> {
            checkIdentifierExists(varName, node, table);

            if (node.getOptional(JmmAttributes.FOR_STMT.OP).isPresent()) {
                checkForUpdateIsInt(varName, node, table);
            }
        });

        return null;
    }

    private Void visitFieldAccess(JmmNode node, SymbolTable table) {
        var receiver = node.getChild(0);
        var fieldName = node.get("name");

        var types = TypeUtils.with(table);
        try {
            var receiverType = types.getExprType(receiver);
            if (receiverType.isClass()
                    && receiverType.asClass().staticRef()
                    && types.sameClass(receiverType.asClass().fullyQualifiedName(), table.getFullyQualifiedName())
                    && table.getField(fieldName).isPresent()) {
                var isStaticContext = node.getAncestor(JmmKind.METHOD_DECL)
                        .map(this::isStaticMethod)
                        .orElse(false);

                if (!isStaticContext && node.getAncestor(JmmKind.MAIN_METHOD_DECL).isPresent()) {
                    isStaticContext = true;
                }

                if (isStaticContext) {
                    addReport(newError(node, "Cannot access instance field '" + fieldName + "' from a static context."));
                    return null;
                }
            }
        } catch (RuntimeException ignored) {
        }

        if (isCurrentClassInstance(receiver, table) && table.getField(fieldName).isEmpty()) {
            addReport(newError(node, "Field '" + fieldName + "' does not exist."));
        }

        return null;
    }

    private void checkIdentifierExists(String idName, JmmNode node, SymbolTable table) {
        var types = TypeUtils.with(table);
        var methodNode = node.getAncestor(JmmKind.METHOD_DECL);
        var isStaticMethod = methodNode
                .map(this::isStaticMethod)
                .orElse(false);

        var isLocalOrParam = methodNode
                .map(method -> isLocalOrParam(idName, method))
                .orElse(false);

        var isCurrentClass = types.isCurrentClassName(idName);
        var isImportedClass = types.isImportedOrImplicitClassName(idName);
        var hasField = table.getField(idName).isPresent();

        if (!isLocalOrParam && hasField && isStaticMethod) {
            addReport(newError(node, "Cannot access instance variable '" + idName + "' from a static context."));
            return;
        }

        var isField = !isStaticMethod && hasField;

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

    private boolean isCurrentClassInstance(JmmNode receiver, SymbolTable table) {
        try {
            var types = TypeUtils.with(table);
            JmmType receiverType = types.getExprType(receiver);

            return receiverType.isClass()
                    && !receiverType.asClass().staticRef()
                    && types.sameClass(receiverType.asClass().fullyQualifiedName(), table.getFullyQualifiedName());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void checkForUpdateIsInt(String idName, JmmNode forStmt, SymbolTable table) {
        var idType = getIdentifierType(idName, forStmt, table);

        if (idType.isPresent() && idType.get().equals(TypeUtils.intType())) {
            return;
        }

        addReport(newError(forStmt, "For update with '" + forStmt.get(JmmAttributes.FOR_STMT.OP) + "' requires int variable '" + idName + "'."));
    }

    private java.util.Optional<JmmType> getIdentifierType(String idName, JmmNode node, SymbolTable table) {
        var methodNode = node.getAncestor(JmmKind.METHOD_DECL);

        if (methodNode.isPresent()) {
            var types = TypeUtils.with(table);
            var signature = types.getMethodDeclSignature(methodNode.get());
            var method = table.getMethod(signature);

            if (method.isPresent()) {
                var localType = method.get().getLocalVariable(idName).map(Symbol::type);
                if (localType.isPresent()) {
                    return localType;
                }

                var paramType = method.get().getParameter(idName).map(Symbol::type);
                if (paramType.isPresent()) {
                    return paramType;
                }
            }
        }

        return table.getField(idName).map(Symbol::type);
    }
}
