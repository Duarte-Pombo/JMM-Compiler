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
    private String fullyQualifiedName;
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
        var importedClassPaths = new HashMap<String, String>();
        for (var importNode : importDecls) {
            var pathList = importNode.getObjectAsList("path", String.class);
            var fullImport = String.join(".", pathList);
            if (!importer.inClassPath(fullImport)) {
                reports.add(newError(importNode, "Imported class '" + fullImport + "' does not exist in the classpath."));
            }
            var importedClassName = pathList.get(pathList.size() - 1);
            var importPath = pathList.size() > 1
                    ? String.join(".", pathList.subList(0, pathList.size() - 1))
                    : "";

            var existingPath = importedClassPaths.putIfAbsent(importedClassName, importPath);
            if (existingPath != null && !existingPath.equals(importPath)) {
                reports.add(newError(importNode, "'" + importPath + "." + importedClassName + "' is already defined"));
            }
            if (!imports.contains(fullImport)) { this.imports.add(fullImport); }
        }
        

        // Given code
        var packageDecl = root.getChildren(PACKAGE_DECL).getFirst();
        var packagePathList = packageDecl.getObjectAsList("path", String.class);
        var packagePath = String.join(".", packagePathList);


        var classDecl = root.getObject("classNode", JmmNode.class);
        SpecsCheck.checkArgument(CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);

        this.className = classDecl.get("name");
        this.fullyQualifiedName = packagePath + "." + className;

        // Check if className is available
        if (declaredClasses.containsKey(className)) {
            reports.add(newError(root, "'" + className + "' is already defined in this compilation unit"));
        }
        declaredClasses.put(className, fullyQualifiedName);


        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnType = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        // Resolve imported superclasses to their fully qualified name and default to Object
        var superClassNameFull = classDecl.getOptional("parent").map(parentName -> {

            var resolvedName = this.resolveQualifiedClassName(parentName);

            if (parentName.equals(this.className)) {
                reports.add(newError(classDecl, "Class '" + this.className + "' cannot extend itself."));
            }
            else if (parentName.equals(resolvedName) && !importer.isImplicitImport(parentName)) {
                reports.add(newError(classDecl, "Superclass '" + parentName + "' is not imported."));
            }

            return resolvedName;

        }).orElse("Object");

        var symbolTable = new JmmSymbolTable(imports, fullyQualifiedName, superClassNameFull, fields, methods, returnType, params, locals, importer);

        return new SymbolTableBuilderResult(symbolTable, reports);
    }

    private List<Symbol> buildFields(JmmNode classDecl) {
        var fields = new ArrayList<Symbol>();
        var fieldNames = new HashSet<String>();

        for (var varDecl : classDecl.getChildren(VAR_DECL)) {
            var field = buildField(varDecl);

            if (!fieldNames.add(field.name())) {
                reports.add(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDecl),
                        NodeUtils.getColumn(varDecl),
                        "Duplicate field name: " + field.name(),
                        null));
                continue;
            }

            fields.add(field);
        }

        return fields;
    }

    private Symbol buildField(JmmNode varDecl) {
        var fieldName = varDecl.get(JmmAttributes.VAR_DECL.NAME);
        var typeNode = varDecl.getChildren().getFirst();
        var type = TypeUtils.convertType(typeNode,imports,this.fullyQualifiedName);

        return new Symbol(type, fieldName);

    }

    private List<MethodSymbol> buildMethods(JmmNode classDecl) {
        var methods = new ArrayList<MethodSymbol>();
        var signatures = new HashSet<pt.up.fe.comp.jmm.analysis.table.Signature>();

        for (var methodNode : classDecl.getChildren(METHOD_DECL)) {
            var method = buildMethod(methodNode);

            if (!signatures.add(method.signature())) {
                reports.add(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodNode),
                        NodeUtils.getColumn(methodNode),
                        "Duplicate method signature: " + method.signature(),
                        null));
                continue;
            }

            methods.add(method);
        }

        return methods;

    }

    // The validations inside this are for method scope only
    private MethodSymbol buildMethod(JmmNode method) {
        var methodName = method.get("name");

        var returnType = buildReturnType(method);


        // 1. Build and validate parameters
        var params = new ArrayList<Symbol>();
        var paramNames = new HashSet<String>();

        if (MAIN_METHOD_DECL.check(method)) {
            var mainParam = buildMainParam(method);
            params.add(mainParam);
            paramNames.add(mainParam.name());
        } else {
            for (var paramNode : method.getChildren(PARAM)) {
                var paramName = paramNode.get("name");

                // .add() returns false if the name is already in the set!
                if (!paramNames.add(paramName)) {
                    reports.add(newError(method, "Duplicate parameter name: " + paramName));
                }

                var paramTypeNode = paramNode.getChildren().getFirst();
                var paramType = TypeUtils.convertType(paramTypeNode,imports,this.fullyQualifiedName);
                params.add(new Symbol(paramType, paramName));
            }
        }

        // 2. Build and validate local variables
        var locals = new ArrayList<Symbol>();
        var localNames = new HashSet<String>();

        for (var varDecl : method.getChildren(VAR_DECL)) {
            var varName = varDecl.get("name");

            // Check against parameters, and check/add to localNames
            if (paramNames.contains(varName) || !localNames.add(varName)) {
                reports.add(newError(method, "Duplicate local variable name: " + varName));
            }

            var varTypeNode = varDecl.getChildren().getFirst();
            var varType = TypeUtils.convertType(varTypeNode,imports,this.fullyQualifiedName);
            locals.add(new Symbol(varType, varName));
        }

        var visibility = Visibility.PACKAGE_PROTECTED;
        if (method.getOptional("visibility").isPresent()) {
            String visStr = method.get("visibility");
            if (visStr.equals("public")) visibility = Visibility.PUBLIC;
            else if (visStr.equals("private")) visibility = Visibility.PRIVATE;
            else if (visStr.equals("protected")) visibility = Visibility.PROTECTED;
        }

        var isStatic = method.getBoolean(JmmAttributes.METHOD_DECL.IS_STATIC, false);
        return new MethodSymbol(methodName, returnType, params, locals, isStatic, visibility);
    }

    private String resolveQualifiedClassName(String className) {
        return imports.stream()
                .filter(importName -> importName.equals(className) || importName.endsWith("." + className))
                .findFirst()
                .orElse(className);
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        Map<String, List<Symbol>> locals = new HashMap<>();
        classDecl.getChildren(METHOD_DECL)
                .forEach(method ->
                        locals.put(
                                method.get("name"),
                                method.getChildren(VAR_DECL).stream().map(this::buildLocal).toList()
                        )
                );

        return locals;
    }

    private Symbol buildLocal(JmmNode varDecl) {
        var name = varDecl.get(JmmAttributes.VAR_DECL.NAME);
        var type = TypeUtils.convertType(varDecl.getChildren().getFirst(), imports, this.fullyQualifiedName);
        return new Symbol(type, name);
    }

    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> params = new HashMap<>();
        classDecl.getChildren(METHOD_DECL)
                .forEach(method ->
                        params.put(
                                method.get("name"),
                                buildMethodParams(method)
                        )
                );

        return params;
    }

    private List<Symbol> buildMethodParams(JmmNode method) {
        if (MAIN_METHOD_DECL.check(method)) {
            return List.of(buildMainParam(method));
        }

        return method.getChildren(PARAM).stream().map(this::buildParam).toList();
    }

    private Symbol buildParam(JmmNode param) {
        var name = param.get(JmmAttributes.PARAM.NAME);
        var type = TypeUtils.convertType(param.getChildren().getFirst(), imports, this.fullyQualifiedName);
        return new Symbol(type, name);
    }

    private Symbol buildMainParam(JmmNode mainMethod) {
        return new Symbol(TypeUtils.stringArrayType(), mainMethod.get("args"));
    }

    private Map<String, JmmType> buildReturnTypes(JmmNode classDecl) {
        Map<String, JmmType> returnTypes = new HashMap<>();

        classDecl.getChildren(METHOD_DECL)
                .forEach(method ->
                        returnTypes.put(
                                method.get("name"),
                                buildReturnType(method)
                        )
                );

        return returnTypes;
    }

    private JmmType buildReturnType(JmmNode methodDecl) {
        SpecsCheck.checkArgument(METHOD_DECL.check(methodDecl), () -> "Expected a method declaration: " + methodDecl);

        if (GENERAL_METHOD_DECL.check(methodDecl)) {
            var typeNode = methodDecl.getObject(JmmAttributes.GENERAL_METHOD_DECL.TYPE_NODE, JmmNode.class);
            return TypeUtils.convertType(typeNode, imports, this.fullyQualifiedName);
        }

        if (MAIN_METHOD_DECL.check(methodDecl)) {
            return JmmPrimitiveType.VOID;
        }

        var typeNode = methodDecl.getChildren().getFirst();
        return TypeUtils.convertType(typeNode, imports, this.fullyQualifiedName);
    }
}
