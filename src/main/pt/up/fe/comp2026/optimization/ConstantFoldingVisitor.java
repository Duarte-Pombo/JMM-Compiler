package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp2026.jmm.ast.JmmKind;


public class ConstantFoldingVisitor  extends PostorderJmmVisitor<SymbolTable, Void> {

    private boolean modified = false;

    @Override
    protected void buildVisitor() {
        addVisit(JmmKind.BINARY_EXPR, this::handleBinaryExpr);
        addVisit(JmmKind.UNARY_EXPR, this::handleUnaryExpr);
        addVisit(JmmKind.NEGATION_EXPR, this::handleNegationExpr);
        setDefaultVisit((node, table) -> null);
    }

    private Void handleBinaryExpr (JmmNode node, SymbolTable table) {

        var left = node.getChild(0);
        var right = node.getChild(1);
        var op = node.get("op");

        String resultValue = null;
        JmmKind newNodeKind = null;

        if (!isLiteral(left) || !isLiteral(right)) {
            return null;
        }

        switch (op) {
            case "+": {
                var leftInt = Integer.parseInt(left.get("value"));
                var rightInt = Integer.parseInt(right.get("value"));
                resultValue = String.valueOf(leftInt + rightInt);
                newNodeKind = JmmKind.INTEGER_LITERAL;
                break;
            }
            case "-": {
                var leftInt = Integer.parseInt(left.get("value"));
                var rightInt = Integer.parseInt(right.get("value"));
                resultValue = String.valueOf(leftInt - rightInt);
                newNodeKind = JmmKind.INTEGER_LITERAL;
                break;
            }
            case "*": {
                var leftInt = Integer.parseInt(left.get("value"));
                var rightInt = Integer.parseInt(right.get("value"));
                resultValue = String.valueOf(leftInt * rightInt);
                newNodeKind = JmmKind.INTEGER_LITERAL;
                break;
            }
            case "/": {
                var leftInt = Integer.parseInt(left.get("value"));
                var rightInt = Integer.parseInt(right.get("value"));
                if (rightInt == 0) return null;
                resultValue = String.valueOf(leftInt / rightInt);
                newNodeKind = JmmKind.INTEGER_LITERAL;
                break;
            }
            case "%": {
                var leftInt = Integer.parseInt(left.get("value"));
                var rightInt = Integer.parseInt(right.get("value"));
                resultValue = String.valueOf(leftInt % rightInt);
                newNodeKind = JmmKind.INTEGER_LITERAL;
                break;
            }
            case "<": {
                var leftInt = Integer.parseInt(left.get("value"));
                var rightInt = Integer.parseInt(right.get("value"));
                resultValue = String.valueOf(leftInt < rightInt);
                newNodeKind = JmmKind.BOOLEAN_LITERAL;
                break;
            }
            case "<=": {
                var leftInt = Integer.parseInt(left.get("value"));
                var rightInt = Integer.parseInt(right.get("value"));
                resultValue = String.valueOf(leftInt <= rightInt);
                newNodeKind = JmmKind.BOOLEAN_LITERAL;
                break;
            }
            case ">": {
                var leftInt = Integer.parseInt(left.get("value"));
                var rightInt = Integer.parseInt(right.get("value"));
                resultValue = String.valueOf(leftInt > rightInt);
                newNodeKind = JmmKind.BOOLEAN_LITERAL;
                break;
            }
            case ">=": {
                var leftInt = Integer.parseInt(left.get("value"));
                var rightInt = Integer.parseInt(right.get("value"));
                resultValue = String.valueOf(leftInt >= rightInt);
                newNodeKind = JmmKind.BOOLEAN_LITERAL;
                break;
            }
            case "&&": {
                var leftBool = Boolean.parseBoolean(left.get("value"));
                var rightBool = Boolean.parseBoolean(right.get("value"));
                resultValue = String.valueOf(leftBool && rightBool);
                newNodeKind = JmmKind.BOOLEAN_LITERAL;
                break;
            }
            case "||": {
                var leftBool = Boolean.parseBoolean(left.get("value"));
                var rightBool = Boolean.parseBoolean(right.get("value"));
                resultValue = String.valueOf(leftBool || rightBool);
                newNodeKind = JmmKind.BOOLEAN_LITERAL;
                break;
            }
            case "==": {
                if (left.getKind().equals(JmmKind.INTEGER_LITERAL.toString())) {
                    var leftInt = Integer.parseInt(left.get("value"));
                    var rightInt = Integer.parseInt(right.get("value"));
                    resultValue = String.valueOf(leftInt == rightInt);
                } else {
                    var leftBool = Boolean.parseBoolean(left.get("value"));
                    var rightBool = Boolean.parseBoolean(right.get("value"));
                    resultValue = String.valueOf(leftBool == rightBool);
                }
                newNodeKind = JmmKind.BOOLEAN_LITERAL;
                break;
            }
            case "!=": {
                if (left.getKind().equals(JmmKind.INTEGER_LITERAL.toString())) {
                    var leftInt = Integer.parseInt(left.get("value"));
                    var rightInt = Integer.parseInt(right.get("value"));
                    resultValue = String.valueOf(leftInt != rightInt);
                } else {
                    var leftBool = Boolean.parseBoolean(left.get("value"));
                    var rightBool = Boolean.parseBoolean(right.get("value"));
                    resultValue = String.valueOf(leftBool != rightBool);
                }
                newNodeKind = JmmKind.BOOLEAN_LITERAL;
                break;
            }
        }

        if (resultValue != null) {
            JmmNode newNode = new JmmNodeImpl(newNodeKind);
            newNode.put("value", resultValue);
            node.replace(newNode);

            this.modified = true;
        }

        return null;
    }

    private Void handleUnaryExpr(JmmNode node, SymbolTable table) {
        var child = node.getChild(0);
        var op = node.get("op");

        if (!child.getKind().equals(JmmKind.INTEGER_LITERAL.toString())) {
            return null;
        }

        String resultValue = null;
        int val = Integer.parseInt(child.get("value"));

        switch (op) {
            case "-":
                resultValue = String.valueOf(-val);
                break;
            case "+":
                resultValue = String.valueOf(val);
                break;
            case "++":
            case "--":
                return null;
        }

        if (resultValue != null) {
            JmmNode newNode = new JmmNodeImpl(JmmKind.INTEGER_LITERAL);
            newNode.put("value", resultValue);
            node.replace(newNode);
            this.modified = true;
        }

        return null;
    }

    private Void handleNegationExpr(JmmNode node, SymbolTable table) {
        var child = node.getChild(0);

        if (!child.getKind().equals(JmmKind.BOOLEAN_LITERAL.toString())) {
            return null;
        }

        boolean val = Boolean.parseBoolean(child.get("value"));
        String resultValue = String.valueOf(!val);

        JmmNode newNode = new JmmNodeImpl(JmmKind.BOOLEAN_LITERAL);
        newNode.put("value", resultValue);
        node.replace(newNode);

        this.modified = true;

        return null;
    }

    private boolean isLiteral(JmmNode node) {
        return node.isInstance(JmmKind.INTEGER_LITERAL) || node.isInstance(JmmKind.BOOLEAN_LITERAL);
    }

    public boolean isModified() {
        return modified;
    }
}
