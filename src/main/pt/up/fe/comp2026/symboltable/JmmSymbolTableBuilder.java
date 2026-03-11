package pt.up.fe.comp2026.symboltable;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Visibility;
import pt.up.fe.comp.jmm.analysis.table.reflection.Importer;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp.jmm.utils.Attributes;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

public class JmmSymbolTableBuilder {

    private final JmmNode root;
    private final Importer importer;
    public String className;
    private final List<Report> reports;
    private final List<String> imports;
    private final Map<String, String> declaredClasses;

    /**
     * Only build() can create new instances, this ensures that each instance is used only once,
     * and we do not have to worry about "cleaning state".
     */
    private JmmSymbolTableBuilder(JmmNode root) {
        this.root = root;
        reports = new ArrayList<>();
        imports = new ArrayList<>();
        declaredClasses = new HashMap<>();
        this.importer = Importer.fromThisClassPath();
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(node),
                NodeUtils.getColumn(node),
                message,
                null);
    }

    public static SymbolTableBuilderResult build(JmmNode root) {
        return new JmmSymbolTableBuilder(root).buildInternal();
    }

    private SymbolTableBuilderResult buildInternal() {

        // New additions
        // Import
        var importDecls = root.getChildren(IMPORT_DECL); //.getFirst().get(JmmAttributes.IMPORT_DECLARATION.PATH);
        for (var importNode : importDecls) {
            var pathList = importNode.getObjectAsList("path", String.class);
            var fullImport = String.join(".", pathList);
            if (!imports.contains(fullImport)) { this.imports.add(fullImport); }
        }
        

        // Given code
        var packageDecl = root.getChildren(PACKAGE_DECL).getFirst();
        var packagePathList = packageDecl.getObjectAsList("path", String.class);
        var packagePath = String.join(".", packagePathList);


        var classDecl = root.getObject("classNode", JmmNode.class);
        SpecsCheck.checkArgument(CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);

        this.className = classDecl.get("name");
        var fullyQualifiedName = packagePath + "." + className;

        // Check if className is available
        if (declaredClasses.containsKey(className)) {
            reports.add(newError(root, "'" + className + "' is already defined in this compilation unit"));
        }
        declaredClasses.put(className, fullyQualifiedName);


        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);

        // Including expands and defaulting to Object
        var superClassName = classDecl.getOptional("parent").orElse("Object");

        var symbolTable = new JmmSymbolTable(imports, fullyQualifiedName, superClassName, fields, methods, importer);

        // 👇 Uncommenting displays the  SYMBOL TABLE 👇
        //System.out.println("\n========================================");
        //System.out.println(" SYMBOL TABLE FOR: " + className);
        //System.out.println("========================================");
        //System.out.println(symbolTable.print()); // (or just System.out.println(symbolTable);)
        //System.out.println("========================================\n");

        return new SymbolTableBuilderResult(symbolTable, reports);
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        return classDecl.getChildren(VAR_DECL).stream()
                .map(this::buildField)
                .toList();
    }

    private Symbol buildField(JmmNode varDecl) {
        var fieldName = varDecl.get(JmmAttributes.VAR_DECL.NAME);
        var typeNode = varDecl.getChildren().getFirst();
        var type = TypeUtils.convertType(typeNode,imports);

        return new Symbol(type, fieldName);

    }

    private List<MethodSymbol> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                .map(this::buildMethod)
                .toList();

    }

    private MethodSymbol buildMethod(JmmNode method) {
        var methodName = method.get("name");

        System.out.println("\n------buildMethod------\n");
        System.out.println(method);
        System.out.println(methodName);
        System.out.println("\n------End------\n");

        var typeNode = method.getChildren().getFirst();
        var returnType = TypeUtils.convertType(typeNode,imports);


        // 1. Build and validate parameters in ONE pass
        var params = new ArrayList<Symbol>();
        var paramNames = new HashSet<String>();

        for (var paramNode : method.getChildren(PARAM)) {
            var paramName = paramNode.get("name");

            // .add() returns false if the name is already in the set!
            if (!paramNames.add(paramName)) {
                reports.add(newError(method, "Duplicate parameter name: " + paramName));
            }

            var paramTypeNode = paramNode.getChildren().getFirst();
            var paramType = TypeUtils.convertType(paramTypeNode,imports);
            params.add(new Symbol(paramType, paramName));
        }

        // 2. Build and validate local variables in ONE pass
        var locals = new ArrayList<Symbol>();
        var localNames = new HashSet<String>();

        for (var varDecl : method.getChildren(VAR_DECL)) {
            var varName = varDecl.get("name");

            // Check against parameters, and check/add to localNames
            if (paramNames.contains(varName) || !localNames.add(varName)) {
                reports.add(newError(method, "Duplicate local variable name: " + varName));
            }

            var varTypeNode = varDecl.getChildren().getFirst();
            var varType = TypeUtils.convertType(varTypeNode,imports);
            locals.add(new Symbol(varType, varName));
        }

        var visibility = Visibility.PUBLIC;
            if (method.getOptional("visibility").isPresent()) {
                String visStr = method.get("visibility");
                if (visStr.equals("private")) visibility = Visibility.PRIVATE;
                else if (visStr.equals("protected")) visibility = Visibility.PROTECTED;
            }

        var isStatic = method.getBoolean(JmmAttributes.METHOD_DECL.IS_STATIC, false);
        return new MethodSymbol(methodName, returnType, params, locals, isStatic, visibility);
    }


}
