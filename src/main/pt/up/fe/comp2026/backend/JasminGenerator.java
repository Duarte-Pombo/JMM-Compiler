package pt.up.fe.comp2026.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import org.specs.comp.ollir.type.ArrayType;
import org.specs.comp.ollir.type.BuiltinKind;
import org.specs.comp.ollir.type.BuiltinType;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2026.optimization.OptUtils;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";
    private static final String SPACE = " ";

    private final OllirResult ollirResult;

    private List<Report> reports;

    private String code;

    private Method currentMethod;

    boolean isInsideAssignment;

    private int currentStack;
    private int maxStack;

    private final JasminUtils types;
    private OptUtils utils;
    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;
        isInsideAssignment = false;
        currentStack = 0;
        maxStack = 0;

        types = new JasminUtils(ollirResult);
        // Initialize everytime we start a method
        utils = null;
        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(NewInstruction.class, this::generateNew);
        generators.put(InvokeSpecialInstruction.class, this::generateInvokeSpecial);
        generators.put(InvokeVirtualInstruction.class, this::generateInvokeVirtual);
        generators.put(InvokeStaticInstruction.class, this::generateInvokeStatic);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(OpCondInstruction.class, this::generateOpCond);
    }


    private String apply(TreeNode node) {
        var code = new StringBuilder();

        // Print the corresponding OLLIR code as a comment
        //code.append("; ").append(node).append(NL);

        code.append(generators.apply(node));

        return code.toString();
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var nameWithPackage = classUnit.getClassFullyQualifiedName().replace('.', '/');
        code.append(".class public ").append(nameWithPackage).append(NL);

        var fullSuperClass = types.getClassPath(classUnit.getSuperClass());
        code.append(".super ").append(fullSuperClass).append(NL).append(NL);

        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    .limit stack 1
                    .limit locals 1
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(fullSuperClass);
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {
        //System.out.println("STARTING METHOD " + method.getMethodName());
        // set method
        currentMethod = method;
        currentStack = 0;
        maxStack = 0;

        // Initialize utils, to have fresh labels
        utils = new OptUtils(null);

        var code = new StringBuilder();

        var modifier = types.getModifier(method.getMethodAccessModifier());


        var staticMod = method.isStaticMethod() ? "static " : "";

        var methodName = method.getMethodName();

        var params = method.getParams().stream()
                .map(p -> types.getTypeDescriptor(p.getType()))
                .collect(Collectors.joining());

        var returnType = types.getTypeDescriptor(method.getReturnType());

        code.append("\n.method ").append(modifier)
                .append(staticMod)
                .append(methodName)
                .append("(" + params + ")" + returnType).append(NL);


        var bodyCode = new StringBuilder();
        for (var inst : method.getInstructions()) {
            method.getLabels(inst).forEach(label -> bodyCode.append(label).append(":").append(NL));

            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            bodyCode.append(instCode);
        }

        // Add limits
        code.append(TAB).append(".limit stack ").append(maxStack).append(NL);
        code.append(TAB).append(".limit locals ").append(getLimitLocals(method)).append(NL);

        code.append(bodyCode);

        code.append(".end method\n");
        //System.out.println("METHOD:\n" + code);
        // unset method
        currentMethod = null;
        //System.out.println("ENDING METHOD " + method.getMethodName());
        return code.toString();
    }

    private int getLimitLocals(Method method) {
        var maxRegisterLimit = method.getVarTable().values().stream()
                .mapToInt(Descriptor::getVirtualReg)
                .filter(reg -> reg >= 0)
                .max()
                .stream()
                .map(maxReg -> maxReg + 1)
                .findFirst()
                .orElse(0);

        var parameterLimit = method.getParams().size() + (method.isStaticMethod() ? 0 : 1);

        return Math.max(maxRegisterLimit, parameterLimit);
    }

    private void updateStack(int delta) {
        currentStack += delta;
        maxStack = Math.max(maxStack, currentStack);
    }

    private String generateAssign(AssignInstruction assign) {
        try {
            isInsideAssignment = true;


            var code = new StringBuilder();

            // store value in the stack in destination
            var lhs = assign.getDest();

            // generate code for loading what's on the right
            code.append(apply(assign.getRhs()));


            // Assume Operand
            var operand = (Operand) lhs;


            // get register
            var reg = currentMethod.getVarTable().get(operand.getName());

            updateStack(-1);
            code.append(types.getStore(reg)).append(NL);

            return code.toString();
        } finally {
            isInsideAssignment = false;
        }
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        String str = literal.getLiteral();

        if (literal.getType() instanceof BuiltinType builtin && builtin.getKind() == BuiltinKind.STRING) {
            updateStack(1);
            return "ldc " + str + NL;
        }
        int n = Integer.parseInt(str);

        if (n == -1) {
            updateStack(1);
            return "iconst_m1" + NL;
        }
        if (n >= 0 && n <= 5) {
            updateStack(1);
            return "iconst_" + n + NL;
        }
        if (n >= -128 && n <= 127) {
            updateStack(1);
            return "bipush " + n + NL;
        }
        if (n >= -32768 && n <= 32767) {
            updateStack(1);
            return "sipush " + n + NL;
        }
        updateStack(1);
        return "ldc " + n + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());

        if (reg == null) {
            if ("this".equals(operand.getName())) {
                updateStack(1);
                return "aload_0" + NL;
            }

            throw new RuntimeException("Could not find register for operand '" + operand.getName() + "'");
        }

        updateStack(1);
        return types.getLoad(reg) + NL;
    }

    private String generateNew(NewInstruction newInst) {
        var caller = (Operand) newInst.getCaller();

        updateStack(1);
        return "new " + types.getClassPath(caller.getName()) + NL;
    }

    private String generateInvokeSpecial(InvokeSpecialInstruction invokeSpecial) {
        var code = new StringBuilder();

        code.append(apply(invokeSpecial.getCaller()));
        for (var argument : invokeSpecial.getArguments()) {
            code.append(apply(argument));
        }

        var caller = invokeSpecial.getCaller();
        var owner = invokeSpecial.getSuperClass()
                .orElseGet(() -> types.getClassName(caller.getType()));

        code.append("invokespecial ")
                .append(types.getClassPath(owner))
                .append("/")
                .append(getMethodName(invokeSpecial))
                .append(getInvocationDescriptor(invokeSpecial))
                .append(NL);
        updateStack(getInvokeStackDelta(invokeSpecial, true));

        appendPopIfUnused(code, invokeSpecial);

        return code.toString();
    }

    private String generateInvokeVirtual(InvokeVirtualInstruction invokeVirtual) {
        var code = new StringBuilder();

        code.append(apply(invokeVirtual.getCaller()));
        for (var argument : invokeVirtual.getArguments()) {
            code.append(apply(argument));
        }

        code.append("invokevirtual ")
                .append(types.getClassPath(types.getClassName(invokeVirtual.getCaller().getType())))
                .append("/")
                .append(getMethodName(invokeVirtual))
                .append(getInvocationDescriptor(invokeVirtual))
                .append(NL);
        updateStack(getInvokeStackDelta(invokeVirtual, true));

        appendPopIfUnused(code, invokeVirtual);

        return code.toString();
    }

    private String generateInvokeStatic(InvokeStaticInstruction invokeStatic) {
        var code = new StringBuilder();

        for (var argument : invokeStatic.getArguments()) {
            code.append(apply(argument));
        }

        code.append("invokestatic ")
                .append(getStaticOwner(invokeStatic.getCaller()))
                .append("/")
                .append(getMethodName(invokeStatic))
                .append(getInvocationDescriptor(invokeStatic))
                .append(NL);
        updateStack(getInvokeStackDelta(invokeStatic, false));

        appendPopIfUnused(code, invokeStatic);

        return code.toString();
    }

    private String getStaticOwner(Element caller) {
        if (caller instanceof Operand operand) {
            return types.getClassPath(operand.getName());
        }

        return types.getClassPath(types.getClassName(caller.getType()));
    }

    private String getMethodName(CallInstruction call) {
        var methodName = call.getMethodName();

        if (methodName instanceof LiteralElement literal) {
            return stripQuotes(literal.getLiteral());
        }

        if (methodName instanceof Operand operand) {
            return operand.getName();
        }

        return stripQuotes(methodName.toString());
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }

    private String getInvocationDescriptor(CallInstruction call) {
        var params = call.getArguments().stream()
                .map(argument -> types.getTypeDescriptor(argument.getType()))
                .collect(Collectors.joining());

        return "(" + params + ")" + types.getTypeDescriptor(call.getReturnType());
    }

    private int getInvokeStackDelta(CallInstruction call, boolean hasReceiver) {
        var consumed = call.getArguments().size() + (hasReceiver ? 1 : 0);
        var produced = returnsValue(call) ? 1 : 0;

        return produced - consumed;
    }

    private boolean returnsValue(CallInstruction call) {
        return !BuiltinType.is(call.getReturnType(), BuiltinKind.VOID);
    }

    private void appendPopIfUnused(StringBuilder code, CallInstruction call) {
        if (isInsideAssignment || !returnsValue(call)) {
            return;
        }

        code.append("pop").append(NL);
        updateStack(-1);
    }


    private String generateBinaryOp(BinaryOpInstruction binaryOp) {

        var code = new StringBuilder();
        var opType = binaryOp.getOperation().getOpType();

        if (isComparisonOp(opType)) {
            var left = binaryOp.getLeftOperand();
            var right = binaryOp.getRightOperand();
            var trueLabel = utils.nextTemp("cmpTrue");
            var endLabel = utils.nextTemp("cmpEnd");
            var baseStack = currentStack;

            if (right instanceof LiteralElement rightLit && "0".equals(rightLit.getLiteral())) {
                code.append(apply(left));
                var jumpInstruction = getZeroCompareJump(opType);
                code.append(jumpInstruction).append(SPACE).append(trueLabel).append(NL);
                updateStack(-1);
            } else if (left instanceof LiteralElement leftLit && "0".equals(leftLit.getLiteral())) {
                code.append(apply(right));
                var jumpInstruction = getZeroCompareJump(swapComparisonOpType(opType));
                code.append(jumpInstruction).append(SPACE).append(trueLabel).append(NL);
                updateStack(-1);
            } else {
                code.append(apply(left));
                code.append(apply(right));
                var jumpInstruction = getIcmpCompareJump(opType);
                code.append(jumpInstruction).append(SPACE).append(trueLabel).append(NL);
                updateStack(-2);
            }

            baseStack = currentStack;
            code.append("iconst_0").append(NL);
            updateStack(1);
            code.append("goto").append(SPACE).append(endLabel).append(NL);
            currentStack = baseStack;
            code.append(trueLabel).append(":").append(NL);
            code.append("iconst_1").append(NL);
            updateStack(1);
            code.append(endLabel).append(":").append(NL);
            currentStack = baseStack + 1;

            return code.toString();
        }

        // load values on the left and on the right
        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));


        var typePrefix = types.getTypePrefix(binaryOp.getOperation().getTypeInfo());

        // apply operation
        var op = switch (opType) {
            case ADD -> "add";
            case MUL -> "mul";
            case SUB -> "sub";
            case DIV -> "div";
            case REM -> "rem";
            default -> throw new NotImplementedException(opType);
        };

        code.append(typePrefix + op).append(NL);
        updateStack(-1);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        var returnType = returnInst.getReturnType();

        var typePrefix = types.getTypePrefix(returnType);

        // Load operand into the stack, if present
        returnInst.getOperand().ifPresent(op -> code.append(apply(op)));

        code.append(typePrefix).append("return").append(NL);
        returnInst.getOperand().ifPresent(op -> updateStack(-1));

        return code.toString();
    }

    private String generateGoto(GotoInstruction inst) {
        return "goto" + SPACE + inst.getLabel() + NL;
    }

    private String generateSingleOpCond(SingleOpCondInstruction inst) {
        var code = new StringBuilder();

        var operand = inst.getCondition().getSingleOperand();

        code.append(apply(operand));

        code.append("ifne").append(SPACE).append(inst.getLabel()).append(NL);
        updateStack(-1);

        return code.toString();
    }

    private String generateOpCond(OpCondInstruction inst) {
        var condition = inst.getCondition();

        if (condition instanceof UnaryOpInstruction unaryOp) {
            return generateUnaryOpCond(unaryOp, inst.getLabel());
        }

        if (condition instanceof BinaryOpInstruction binaryOp) {
            return generateBinaryOpCond(binaryOp, inst.getLabel());
        }

        throw new NotImplementedException(condition.getClass());
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();
        var opType = unaryOp.getOperation().getOpType();

        return switch (opType) {
            case LOGICAL_NOT -> {
                var trueLabel = utils.nextTemp("notTrue");
                var endLabel = utils.nextTemp("notEnd");
                code.append(apply(unaryOp.getOperand()));
                code.append("ifeq").append(SPACE).append(trueLabel).append(NL);
                updateStack(-1);
                var baseStack = currentStack;
                code.append("iconst_0").append(NL);
                updateStack(1);
                code.append("goto").append(SPACE).append(endLabel).append(NL);
                currentStack = baseStack;
                code.append(trueLabel).append(":").append(NL);
                code.append("iconst_1").append(NL);
                updateStack(1);
                code.append(endLabel).append(":").append(NL);
                currentStack = baseStack + 1;
                yield code.toString();
            }
            default -> throw new NotImplementedException(opType);
        };
    }

    private String generateUnaryOpCond(UnaryOpInstruction unaryOp, String label) {
        var code = new StringBuilder();

        code.append(apply(unaryOp.getOperand()));

        var jumpInstruction = switch (unaryOp.getOperation().getOpType()) {
            case LOGICAL_NOT -> "ifeq";
            default -> throw new NotImplementedException(unaryOp.getOperation().getOpType());
        };

        code.append(jumpInstruction).append(SPACE).append(label).append(NL);
        updateStack(-1);

        return code.toString();
    }

    private boolean isComparisonOp(OperationType opType) {
        return switch (opType) {
            case EQ, NEQ, LTH, GTH, LTE, GTE -> true;
            default -> false;
        };
    }

    private OperationType swapComparisonOpType(OperationType opType) {
        return switch (opType) {
            case LTH -> OperationType.GTH;
            case GTH -> OperationType.LTH;
            case LTE -> OperationType.GTE;
            case GTE -> OperationType.LTE;
            case EQ, NEQ -> opType;
            default -> throw new NotImplementedException(opType);
        };
    }

    private String getZeroCompareJump(OperationType opType) {
        return switch (opType) {
            case EQ -> "ifeq";
            case NEQ -> "ifne";
            case LTH -> "iflt";
            case GTH -> "ifgt";
            case LTE -> "ifle";
            case GTE -> "ifge";
            default -> throw new NotImplementedException(opType);
        };
    }

    private String getIcmpCompareJump(OperationType opType) {
        return switch (opType) {
            case EQ -> "if_icmpeq";
            case NEQ -> "if_icmpne";
            case LTH -> "if_icmplt";
            case GTH -> "if_icmpgt";
            case LTE -> "if_icmple";
            case GTE -> "if_icmpge";
            default -> throw new NotImplementedException(opType);
        };
    }

    private String generateBinaryOpCond(BinaryOpInstruction binaryOp, String label) {
        var code = new StringBuilder();
        var left = binaryOp.getLeftOperand();
        var right = binaryOp.getRightOperand();
        var opType = binaryOp.getOperation().getOpType();

        if (right instanceof LiteralElement rightLit && "0".equals(rightLit.getLiteral())) {
            code.append(apply(left));

            var jumpInstruction = getZeroCompareJump(opType);

            code.append(jumpInstruction).append(SPACE).append(label).append(NL);
            updateStack(-1);
            return code.toString();
        }

        if (left instanceof LiteralElement leftLit && "0".equals(leftLit.getLiteral())) {
            code.append(apply(right));

            var jumpInstruction = getZeroCompareJump(swapComparisonOpType(opType));

            code.append(jumpInstruction).append(SPACE).append(label).append(NL);
            updateStack(-1);
            return code.toString();
        }

        code.append(apply(left));
        code.append(apply(right));

        var jumpInstruction = getIcmpCompareJump(opType);

        code.append(jumpInstruction).append(SPACE).append(label).append(NL);
        updateStack(-2);

        return code.toString();
    }
}
