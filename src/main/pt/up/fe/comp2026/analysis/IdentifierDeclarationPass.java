package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;

import java.util.ArrayList;
import java.util.List;

public class IdentifierDeclarationPass implements AnalysisPass {

    @Override
    public List<Report> analyze(JmmNode root, SymbolTable table) {
        List<Report> reports = new ArrayList<>();
        IdentifierVisitor visitor = new IdentifierVisitor(table, reports);
        visitor.visit(root);
        return reports;
    }

    private static class IdentifierVisitor extends AnalysisVisitorWithTable {
        private final List<Report> reports;

        public IdentifierVisitor(SymbolTable table, List<Report> reports) {
            super(table);
            this.reports = reports;
        }

        @Override
        protected void buildVisitor() {
            addVisit(JmmKind.VAR_REF_EXPR, this::visitVarRefExpr);
            addVisit(JmmKind.ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);

            setDefaultVisit(this::visitAllChildren);
        }

        private Void visitVarRefExpr(JmmNode node, SymbolTable table) {
            checkIdentifierExists(node.get("name"), node, table);
            return null;
        }

        private Void visitArrayAssignStmt(JmmNode node, SymbolTable table) {
            checkIdentifierExists(node.get("var"), node, table);

            visitAllChildren(node, table);
            return null;
        }

        private void checkIdentifierExists(String idName, JmmNode node, SymbolTable table) {
            JmmSymbolTable jmmTable = (JmmSymbolTable) table;

            // 1. Find the enclosing method
            var methodNode = node.getAncestor(JmmKind.GENERAL_METHOD_DECL);
            if (methodNode.isEmpty()) {
                methodNode = node.getAncestor(JmmKind.MAIN_METHOD_DECL);
            }

            if (methodNode.isPresent()) {
                String methodName = methodNode.get().get("name");

                // 2. Check Locals
                var locals = jmmTable.getLocalVariables(methodName);
                boolean isLocal = locals.isPresent() &&
                                  locals.get().stream().anyMatch(symbol -> symbol.name().equals(idName));

                // 3. Check Parameters
                var params = jmmTable.getParameters(methodName);
                boolean isParam = params.isPresent() &&
                                  params.get().stream().anyMatch(symbol -> symbol.name().equals(idName));

                // Figure out if the method we are inside is static (like 'main')
                boolean isStaticMethod = methodNode.get().getKind().equals(JmmKind.MAIN_METHOD_DECL.toString());

                // 3.1 Check Main Method specific 'args' parameter
                if (isStaticMethod) {
                    String argsName = methodNode.get().get("args");
                    if (idName.equals(argsName)) {
                        isParam = true;
                    }
                } else if (methodNode.get().getOptional("isStatic").isPresent()) {
                    isStaticMethod = Boolean.parseBoolean(methodNode.get().get("isStatic"));
                }

                // 4. Check Fields
                boolean isField = !isStaticMethod && jmmTable.getField(idName).isPresent();

                // 5. Check Explicit Imports & Current Class Name
                boolean isClass = jmmTable.getDeclaredClasses().contains(idName);
                boolean isCurrentClass = jmmTable.getFullyQualifiedName().equals(idName) ||
                                         jmmTable.getFullyQualifiedName().endsWith("." + idName);

                // 5.1 Check Implicit Imports
                boolean isImplicit = jmmTable.isImplicitImport(idName);

                // 6. Superclass Inheritance bypass
                boolean hasSuperClass = !isStaticMethod &&
                                        jmmTable.getSuperFullyQualifiedName() != null &&
                                        !jmmTable.getSuperFullyQualifiedName().equals("Object");

                // 7. Report error if not found in any valid scope
                if (!isLocal && !isParam && !isField && !isClass && !isCurrentClass && !isImplicit && !hasSuperClass) {
                    reports.add(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(node),
                            NodeUtils.getColumn(node),
                            "Identifier '" + idName + "' is not declared in this scope.",
                            null)
                    );
                }
            }
        }
    }
}