package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.CompilerConfig;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.optimization.RegisterAllocation.LivenessAnalyzer;
import pt.up.fe.comp2026.optimization.RegisterAllocation.InterferenceGraph;
import pt.up.fe.comp2026.optimization.RegisterAllocation.GraphColoring;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {
        var loweredSemantics = transformAst(semanticsResult);

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(loweredSemantics.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(loweredSemantics.getRootNode());

        return new OllirResult(loweredSemantics, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult transformAst(JmmSemanticsResult semanticsResult) {
        boolean modified;

        var root = semanticsResult.getRootNode();
        var table = semanticsResult.getSymbolTable();

        for (var forStmt : root.getDescendants(FOR_STMT)) {
            convertForStmt(forStmt);
        }

        for (var doWhileStmt : root.getDescendants(DO_WHILE_STMT)) {
            convertDoWhileStmt(doWhileStmt);
        }

        String optimizeFlag = semanticsResult.config().getOrDefault("optimize", "false");
        if ("true".equals(optimizeFlag)) {
            do {
                modified = false;

                ConstantFoldingVisitor folder = new ConstantFoldingVisitor();
                folder.visit(root, table);
                modified |= folder.isModified();

                ConstantPropagationVisitor propagator = new ConstantPropagationVisitor();
                propagator.visit(root, table);
                modified |= propagator.isModified();

                modified |= eliminateBranches(root);

                TypeUtils types = new TypeUtils(semanticsResult.getSymbolTable());
                for (var methodDecl : root.getDescendants(METHOD_DECL)) {
                    modified |= eliminateDeadCode(methodDecl, semanticsResult.getSymbolTable(), types);
                }

            } while (modified);
        }

        return semanticsResult;
    }

    private void convertForStmt(JmmNode forStmt) {
        int childIndex = 0;

        JmmNode initAssign = null;
        if (forStmt.getOptional(JmmAttributes.FOR_STMT.INIT_VAR).isPresent()) {
            var initVar = forStmt.get(JmmAttributes.FOR_STMT.INIT_VAR);
            var initExpr = forStmt.getChild(childIndex++);
            initAssign = buildAssignStmt(initVar, initExpr);
        }

        JmmNode conditionExpr;
        if (childIndex < forStmt.getNumChildren() && forStmt.getChild(childIndex).isInstance(FOR_CONDITION)) {
            conditionExpr = forStmt.getChild(childIndex).getChild(0);
            childIndex++;
        } else {
            var trueLiteral = new JmmNodeImpl(BOOLEAN_LITERAL);
            trueLiteral.put(JmmAttributes.BOOLEAN_LITERAL.VALUE, "true");
            conditionExpr = trueLiteral;
        }

        JmmNode updateAssign = null;
        var hasUpdateVar = forStmt.getOptional(JmmAttributes.FOR_STMT.UPDATE_VAR).isPresent();
        var hasIncDecUpdate = hasUpdateVar && forStmt.getOptional(JmmAttributes.FOR_STMT.OP).isPresent();
        var hasAssignmentUpdate = hasUpdateVar && forStmt.getOptional(JmmAttributes.FOR_STMT.OP).isEmpty();

        if (hasIncDecUpdate) {
            var updateVar = forStmt.get(JmmAttributes.FOR_STMT.UPDATE_VAR);
            var op = forStmt.get(JmmAttributes.FOR_STMT.OP);
            updateAssign = buildIncDecAssignStmt(updateVar, op);
        } else if (hasAssignmentUpdate && childIndex < forStmt.getNumChildren() - 1) {
            var updateVar = forStmt.get(JmmAttributes.FOR_STMT.UPDATE_VAR);
            var updateExpr = forStmt.getChild(childIndex++);
            updateAssign = buildAssignStmt(updateVar, updateExpr);
        }

        var originalBody = forStmt.getChild(forStmt.getNumChildren() - 1);
        var transformedBody = new JmmNodeImpl(COMPOUND_STMT);
        transformedBody.add(originalBody);
        if (updateAssign != null) {
            transformedBody.add(updateAssign);
        }

        var whileNode = new JmmNodeImpl(WHILE_STMT);
        whileNode.add(conditionExpr);
        whileNode.add(transformedBody);

        if (initAssign == null) {
            forStmt.replace(whileNode);
            return;
        }

        var lowered = new JmmNodeImpl(COMPOUND_STMT);
        lowered.add(initAssign);
        lowered.add(whileNode);
        forStmt.replace(lowered);
    }

    private JmmNode buildAssignStmt(String varName, JmmNode rhsExpr) {
        var lhs = new JmmNodeImpl(VAR_REF_EXPR);
        lhs.put(JmmAttributes.VAR_REF_EXPR.NAME, varName);

        var assign = new JmmNodeImpl(ASSIGN_STMT);
        assign.add(lhs);
        assign.add(rhsExpr);
        return assign;
    }

    private JmmNode buildIncDecAssignStmt(String varName, String op) {
        var lhs = new JmmNodeImpl(VAR_REF_EXPR);
        lhs.put(JmmAttributes.VAR_REF_EXPR.NAME, varName);

        var rhsVar = new JmmNodeImpl(VAR_REF_EXPR);
        rhsVar.put(JmmAttributes.VAR_REF_EXPR.NAME, varName);

        var one = new JmmNodeImpl(INTEGER_LITERAL);
        one.put(JmmAttributes.INTEGER_LITERAL.VALUE, "1");

        var binExpr = new JmmNodeImpl(BINARY_EXPR);
        binExpr.put(JmmAttributes.BINARY_EXPR.OP, "++".equals(op) ? "+" : "-");
        binExpr.add(rhsVar);
        binExpr.add(one);

        var assign = new JmmNodeImpl(ASSIGN_STMT);
        assign.add(lhs);
        assign.add(binExpr);
        return assign;
    }

    private void convertDoWhileStmt(JmmNode doWhileStmt) {
        var body = doWhileStmt.getChild(0);
        var condition = doWhileStmt.getChild(1);

        var lowered = new JmmNodeImpl(COMPOUND_STMT);
        lowered.add(body);

        var whileNode = new JmmNodeImpl(WHILE_STMT);
        whileNode.add(condition);
        whileNode.add(cloneSubtree(body));

        lowered.add(whileNode);
        doWhileStmt.replace(lowered);
    }

    private JmmNode cloneSubtree(JmmNode node) {
        var copy = node.copyDeep(node.getKind());
        for (var child : node.getChildren()) {
            copy.add(cloneSubtree(child));
        }
        return copy;
    }

    @Override
    public OllirResult transformOllir(OllirResult ollirResult) {

        if (ollirResult.config().getOrDefault("debug", "false").equals("true")) {
            System.out.println("OLLIR CODE:");
            System.out.println(ollirResult.getOllirCode());
        }

        ollirResult.getOllirClass().buildCFGs();

        var n  = CompilerConfig.getRegisterAllocation(ollirResult.config());

        if (n != -1) {
            for (var method : ollirResult.getOllirClass().getMethods()) {
                var analyzer = new LivenessAnalyzer(method);
                analyzer.computeDefUse();
                analyzer.computeInOut();

                var interferenceGraph = new InterferenceGraph(method, analyzer);
                interferenceGraph.buildGraph();

                int minK = method.isStaticMethod() ? 0 : 1;
                minK += method.getParams().size();
                int maxK = (n == 0) ? Integer.MAX_VALUE : minK + n;

                boolean colored = false;
                int k = minK;
                while (!colored && k <= maxK) {
                    var coloring = new GraphColoring(method, interferenceGraph, k);
                    colored = coloring.colorGraph();
                    if (!colored) {
                        k++;
                    }
                }

                if (!colored) {
                    int minimumLocalRegisters = k - minK;
                    String message = "Register allocation failed for method '" + method.getMethodName()
                            + "': -r=" + n + " allows " + n + " local register(s), but at least "
                            + minimumLocalRegisters + " are required (" + k
                            + " total JVM local variable slot(s), including this/parameters).";

                    ollirResult.reports().add(Report.newError(Stage.LLIR_OPTIMIZATION, -1, -1, message, null));
                    return ollirResult;
                }
            }
        }

        return ollirResult;
    }

    private boolean eliminateBranches(JmmNode root) {
        boolean changed = false;
        var ifStmts = new ArrayList<>(root.getDescendants(IF_ELSE_STMT));

        for (var ifStmt : ifStmts) {
            JmmNode condition = ifStmt.getChild(0);
            Boolean condValue = evaluateStaticCondition(condition);

            if (condValue != null) {
                if (condValue) {
                    ifStmt.replace(ifStmt.getChild(1));
                } else {
                    if (ifStmt.getNumChildren() > 2) {
                        ifStmt.replace(ifStmt.getChild(2));
                    } else {
                        ifStmt.replace(new JmmNodeImpl(COMPOUND_STMT));
                    }
                }
                changed = true;
            }
        }

        return changed;
    }

    private Boolean evaluateStaticCondition(JmmNode node) {
        if (node.isInstance(BOOLEAN_LITERAL)) {
            return "true".equals(node.get("value"));
        }

        if (node.isInstance(PARENTHESES_EXPR)) {
            return evaluateStaticCondition(node.getChild(0));
        }

        if (node.isInstance(NEGATION_EXPR) || node.isInstance(UNARY_EXPR)) {
            var op = node.getOptional("op").orElse("");
            if ("!".equals(op)) {
                Boolean childVal = evaluateStaticCondition(node.getChild(0));
                return childVal != null ? !childVal : null;
            }
        }

        if (node.isInstance(BINARY_EXPR)) {
            String op = node.getOptional("op").orElse("");

            if ("&&".equals(op)) {
                Boolean left = evaluateStaticCondition(node.getChild(0));
                if (Boolean.FALSE.equals(left)) return false; // Short-circuit

                Boolean right = evaluateStaticCondition(node.getChild(1));
                if (Boolean.FALSE.equals(right)) return false;

                if (Boolean.TRUE.equals(left) && Boolean.TRUE.equals(right)) return true;
                return null;
            }

            if ("||".equals(op)) {
                Boolean left = evaluateStaticCondition(node.getChild(0));
                if (Boolean.TRUE.equals(left)) return true; // Short-circuit

                Boolean right = evaluateStaticCondition(node.getChild(1));
                if (Boolean.TRUE.equals(right)) return true;

                if (Boolean.FALSE.equals(left) && Boolean.FALSE.equals(right)) return false;
                return null;
            }
        }

        return null;
    }

    private boolean eliminateDeadCode(JmmNode methodDecl, SymbolTable table, TypeUtils types) {
        boolean changed = false;
        var methodSig = types.getMethodDeclSignature(methodDecl);
        var methodOpt = table.getMethod(methodSig);
        if (methodOpt.isEmpty()) return false;
        var methodSymbol = methodOpt.get();

        var context = new DeadCodeContext(methodSymbol, findExternallyBackedArrayLocals(methodDecl, methodSymbol, table));

        changed |= eliminateUnreachableStatements(methodDecl);
        changed |= eliminateDeadAssignmentsInSequence(methodDecl, Collections.emptySet(), context).changed();

        return changed;
    }

    private DceResult eliminateDeadAssignmentsInSequence(JmmNode container, Set<String> liveAfter, DeadCodeContext context) {
        boolean changed = false;
        Set<String> live = new HashSet<>(liveAfter);
        List<JmmNode> statements = new ArrayList<>(container.getChildren(STMT));

        for (int i = statements.size() - 1; i >= 0; i--) {
            var result = eliminateDeadAssignments(statements.get(i), live, context);
            live = result.liveBefore();
            changed |= result.changed();
        }

        return new DceResult(live, changed);
    }

    private DceResult eliminateDeadAssignments(JmmNode stmt, Set<String> liveAfter, DeadCodeContext context) {
        if (stmt.isInstance(COMPOUND_STMT)) {
            return eliminateDeadAssignmentsInSequence(stmt, liveAfter, context);
        }

        if (stmt.isInstance(RETURN_STMT)) {
            return new DceResult(readLocalVariables(stmt, context.method()), false);
        }

        if (stmt.isInstance(ASSIGN_STMT)) {
            return eliminateAssignStmt(stmt, liveAfter, context);
        }

        if (stmt.isInstance(ARRAY_ASSIGN_STMT)) {
            return eliminateArrayAssignStmt(stmt, liveAfter, context);
        }

        if (stmt.isInstance(EXPR_STMT)) {
            var expr = stmt.getChild(0);
            if (!hasSideEffects(expr)) {
                stmt.replace(new JmmNodeImpl(COMPOUND_STMT));
                return new DceResult(new HashSet<>(liveAfter), true);
            }

            var liveBefore = new HashSet<>(liveAfter);
            liveBefore.addAll(readLocalVariables(expr, context.method()));
            return new DceResult(liveBefore, false);
        }

        if (stmt.isInstance(IF_ELSE_STMT)) {
            var thenResult = eliminateDeadAssignments(stmt.getChild(1), liveAfter, context);
            var elseResult = stmt.getNumChildren() > 2
                    ? eliminateDeadAssignments(stmt.getChild(2), liveAfter, context)
                    : new DceResult(new HashSet<>(liveAfter), false);

            var liveBefore = new HashSet<>(thenResult.liveBefore());
            liveBefore.addAll(elseResult.liveBefore());
            liveBefore.addAll(readLocalVariables(stmt.getChild(0), context.method()));

            return new DceResult(liveBefore, thenResult.changed() || elseResult.changed());
        }

        if (stmt.isInstance(WHILE_STMT)) {
            var liveBefore = new HashSet<>(liveAfter);
            liveBefore.addAll(readLocalVariables(stmt.getChild(0), context.method()));

            var bodyLiveAfter = new HashSet<>(liveBefore);
            bodyLiveAfter.addAll(readLocalVariables(stmt.getChild(1), context.method()));

            var bodyResult = eliminateDeadAssignments(stmt.getChild(1), bodyLiveAfter, context);
            liveBefore.addAll(bodyResult.liveBefore());

            return new DceResult(liveBefore, bodyResult.changed());
        }

        var liveBefore = new HashSet<>(liveAfter);
        liveBefore.addAll(readLocalVariables(stmt, context.method()));
        return new DceResult(liveBefore, false);
    }

    private DceResult eliminateAssignStmt(JmmNode assign, Set<String> liveAfter, DeadCodeContext context) {
        var lhs = assign.getChild(0);
        var rhs = assign.getChild(1);

        if (!lhs.isInstance(VAR_REF_EXPR)) {
            var liveBefore = new HashSet<>(liveAfter);
            liveBefore.addAll(readLocalVariables(lhs, context.method()));
            liveBefore.addAll(readLocalVariables(rhs, context.method()));
            return new DceResult(liveBefore, false);
        }

        String varName = lhs.get("name");
        if (!isLocalOrParameter(varName, context.method())) {
            var liveBefore = new HashSet<>(liveAfter);
            liveBefore.addAll(readLocalVariables(rhs, context.method()));
            return new DceResult(liveBefore, false);
        }

        if (!liveAfter.contains(varName)) {
            return eliminateDeadStmtKeepingSideEffects(assign, liveAfter, context.method(), rhs);
        }

        var liveBefore = new HashSet<>(liveAfter);
        liveBefore.remove(varName);
        liveBefore.addAll(readLocalVariables(rhs, context.method()));
        return new DceResult(liveBefore, false);
    }

    private DceResult eliminateArrayAssignStmt(JmmNode arrayAssign, Set<String> liveAfter, DeadCodeContext context) {
        String varName = arrayAssign.get("var");
        boolean mutatesExternalArray = !isLocalVariable(varName, context.method())
                || context.externallyBackedArrayLocals().contains(varName);

        if (mutatesExternalArray || liveAfter.contains(varName)) {
            var liveBefore = new HashSet<>(liveAfter);
            if (isLocalOrParameter(varName, context.method())) {
                liveBefore.add(varName);
            }
            for (var child : arrayAssign.getChildren()) {
                liveBefore.addAll(readLocalVariables(child, context.method()));
            }
            return new DceResult(liveBefore, false);
        }

        return eliminateDeadStmtKeepingSideEffects(
                arrayAssign,
                liveAfter,
                context.method(),
                arrayAssign.getChildren().toArray(JmmNode[]::new)
        );
    }

    private DceResult eliminateDeadStmtKeepingSideEffects(JmmNode stmt, Set<String> liveAfter, MethodSymbol method, JmmNode... expressions) {
        var liveBefore = new HashSet<>(liveAfter);
        var sideEffectStmts = new ArrayList<JmmNode>();

        for (var expression : expressions) {
            if (!hasSideEffects(expression)) continue;

            var exprStmt = new JmmNodeImpl(EXPR_STMT);
            exprStmt.add(expression);
            sideEffectStmts.add(exprStmt);
            liveBefore.addAll(readLocalVariables(expression, method));
        }

        if (sideEffectStmts.isEmpty()) {
            stmt.replace(new JmmNodeImpl(COMPOUND_STMT));
        } else if (sideEffectStmts.size() == 1) {
            stmt.replace(sideEffectStmts.getFirst());
        } else {
            var compound = new JmmNodeImpl(COMPOUND_STMT);
            sideEffectStmts.forEach(compound::add);
            stmt.replace(compound);
        }

        return new DceResult(liveBefore, true);
    }

    private boolean eliminateUnreachableStatements(JmmNode container) {
        boolean changed = false;
        boolean unreachable = false;
        List<JmmNode> statements = new ArrayList<>(container.getChildren(STMT));

        for (var statement : statements) {
            if (unreachable) {
                if (!isEmptyCompound(statement)) {
                    statement.replace(new JmmNodeImpl(COMPOUND_STMT));
                    changed = true;
                }
                continue;
            }

            changed |= eliminateUnreachableInside(statement);
            if (statementAlwaysReturns(statement)) {
                unreachable = true;
            }
        }

        return changed;
    }

    private boolean isEmptyCompound(JmmNode statement) {
        return statement.isInstance(COMPOUND_STMT) && statement.getNumChildren() == 0;
    }

    private boolean eliminateUnreachableInside(JmmNode statement) {
        if (statement.isInstance(COMPOUND_STMT)) {
            return eliminateUnreachableStatements(statement);
        }

        if (statement.isInstance(IF_ELSE_STMT)) {
            boolean changed = eliminateUnreachableInside(statement.getChild(1));
            if (statement.getNumChildren() > 2) {
                changed |= eliminateUnreachableInside(statement.getChild(2));
            }
            return changed;
        }

        if (statement.isInstance(WHILE_STMT)) {
            Boolean condition = evaluateStaticCondition(statement.getChild(0));
            if (Boolean.FALSE.equals(condition)) {
                statement.replace(new JmmNodeImpl(COMPOUND_STMT));
                return true;
            }

            return eliminateUnreachableInside(statement.getChild(1));
        }

        return false;
    }

    private boolean statementAlwaysReturns(JmmNode statement) {
        if (statement.isInstance(RETURN_STMT)) {
            return true;
        }

        if (statement.isInstance(COMPOUND_STMT)) {
            return sequenceAlwaysReturns(statement);
        }

        if (statement.isInstance(IF_ELSE_STMT)) {
            return statement.getNumChildren() > 2
                    && statementAlwaysReturns(statement.getChild(1))
                    && statementAlwaysReturns(statement.getChild(2));
        }

        if (statement.isInstance(WHILE_STMT)) {
            return Boolean.TRUE.equals(evaluateStaticCondition(statement.getChild(0)))
                    && statementAlwaysReturns(statement.getChild(1));
        }

        return false;
    }

    private boolean sequenceAlwaysReturns(JmmNode container) {
        for (var statement : container.getChildren(STMT)) {
            if (statementAlwaysReturns(statement)) {
                return true;
            }
        }

        return false;
    }

    private Set<String> findExternallyBackedArrayLocals(JmmNode methodDecl, MethodSymbol method, SymbolTable table) {
        var externalAliases = new HashSet<String>();
        boolean changed;

        do {
            changed = false;

            for (var assign : methodDecl.getDescendants(ASSIGN_STMT)) {
                var lhs = assign.getChild(0);
                if (!lhs.isInstance(VAR_REF_EXPR)) continue;

                String varName = lhs.get("name");
                var local = method.getLocalVariable(varName);
                if (local.isEmpty() || !local.get().type().isArray()) continue;

                if (mayReferToExternalArray(assign.getChild(1), method, table, externalAliases)) {
                    changed |= externalAliases.add(varName);
                }
            }
        } while (changed);

        return externalAliases;
    }

    private boolean mayReferToExternalArray(JmmNode expr, MethodSymbol method, SymbolTable table, Set<String> externalAliases) {
        if (expr.isInstance(PARENTHESES_EXPR)) {
            return mayReferToExternalArray(expr.getChild(0), method, table, externalAliases);
        }

        if (expr.isInstance(NEW_ARRAY) || expr.isInstance(NEW_ARRAY_BY_EXTENSION) || expr.isInstance(ARRAY)) {
            return false;
        }

        if (expr.isInstance(VAR_REF_EXPR)) {
            String varName = expr.get("name");
            boolean isLocal = method.getLocalVariable(varName).isPresent();
            return method.getParameter(varName).isPresent()
                    || (!isLocal && table.getField(varName).isPresent())
                    || externalAliases.contains(varName);
        }

        if (expr.isInstance(FIELD_ACCESS) || expr.isInstance(METHOD_CALL) || expr.isInstance(IMPLICIT_CALL)) {
            return true;
        }

        for (var child : expr.getChildren()) {
            if (mayReferToExternalArray(child, method, table, externalAliases)) {
                return true;
            }
        }

        return false;
    }

    private Set<String> readLocalVariables(JmmNode node, MethodSymbol method) {
        var reads = new HashSet<String>();
        collectLocalReads(node, method, reads);
        return reads;
    }

    private void collectLocalReads(JmmNode node, MethodSymbol method, Set<String> reads) {
        if (node.isInstance(VAR_REF_EXPR)) {
            String varName = node.get("name");
            if (isLocalOrParameter(varName, method)) {
                reads.add(varName);
            }
            return;
        }

        for (var child : node.getChildren()) {
            collectLocalReads(child, method, reads);
        }
    }

    private boolean isLocalOrParameter(String varName, MethodSymbol method) {
        return method.getLocalVariable(varName).isPresent() || method.getParameter(varName).isPresent();
    }

    private boolean isLocalVariable(String varName, MethodSymbol method) {
        return method.getLocalVariable(varName).isPresent();
    }

    private boolean hasSideEffects(JmmNode node) {
        if (node.isInstance(METHOD_CALL) || node.isInstance(IMPLICIT_CALL) || node.isInstance(NEW_OBJECT)) {
            return true;
        }

        if (node.isInstance(UNARY_EXPR)) {
            var op = node.getOptional("op").orElse("");
            if ("++".equals(op) || "--".equals(op)) {
                return true;
            }
        }

        // Recursively check children
        for (JmmNode child : node.getChildren()) {
            if (hasSideEffects(child)) return true;
        }
        return false;
    }

    private record DeadCodeContext(MethodSymbol method, Set<String> externallyBackedArrayLocals) {
    }

    private record DceResult(Set<String> liveBefore, boolean changed) {
    }
}
