package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.CompilerConfig;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.comp2026.optimization.RegisterAllocation.LivenessAnalyzer;
import pt.up.fe.comp2026.optimization.RegisterAllocation.InterferenceGraph;
import pt.up.fe.comp2026.optimization.RegisterAllocation.GraphColoring;
import java.util.ArrayList;
import java.util.Collections;

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

                eliminateBranches(root);

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

    private void eliminateBranches(JmmNode root) {
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
            }
        }
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

}
