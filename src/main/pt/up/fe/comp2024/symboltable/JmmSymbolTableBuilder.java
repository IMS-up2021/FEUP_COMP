package pt.up.fe.comp2024.symboltable;

import static pt.up.fe.comp2024.ast.Kind.*;

import java.util.*;
import java.util.stream.Collectors;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

public class JmmSymbolTableBuilder {

    public static JmmSymbolTable build(JmmNode root) {
        JmmNode classDecl = root;

        var imports = buildImports(root);
        System.out.println(imports);

        for (JmmNode child : root.getChildren()) {
            if (child.getKind().equals("ImportDecl")) {
            } else if (
                    "ClassDecl".equals(child.getKind())
            ) {
                classDecl = child;
            }
        }

        String className = classDecl.get("name");
        String superClassName = buildSuperClassName(classDecl);
        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(
                className,
                superClassName,
                methods,
                imports,
                fields,
                returnTypes,
                params,
                locals
        );
    }

    private static String buildSuperClassName(JmmNode classDecl) {
        System.out.println("Inside buildSuperClassName");
        for (JmmNode child : classDecl.getChildren()) {
            System.out.println("Inside For Loop");
            System.out.println("Child kind: " + child.getKind()); // Print the kind of the child
            if ("ExtendsDecl".equals(child.getKind())) {
                //System.out.println("Inside If");
                //print node
                //System.out.println("Child: " + child);
                return child.get("name");
            }
        }
        return null;
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();

        classDecl
                .getChildren(METHOD_DECL)
                .stream()
                .forEach(method -> {
                    String methodName = method.get("name");
                    //System.out.println("(Builder)Method: " + methodName);

                    // Skip processing for the main method
                    if ("main".equals(methodName)) {
                        map.put(methodName, new Type("void", false));
                        return;
                    }

                    // Get the return type node
                    JmmNode returnTypeNode = method.getChildren().get(0);

                    // Get the type attribute of the return type node
                    String returnType = returnTypeNode.get("declaration");
                    //System.out.println("RETURN TYPE" + returnType);

                    // Check if the return type is an array
                    boolean isArray = "Array".equals(returnTypeNode.getKind());

                    // Create a new Type with the return type and add it to the map
                    map.put(methodName, new Type(returnType, isArray));
                });

        System.out.println("MAP NEXT!!");
        // Print the contents of the map
        map.forEach((key, value) ->
                System.out.println(
                        "Method: " +
                                key +
                                ", Return type: " +
                                value.getName() +
                                ", Is array: " +
                                value.isArray()
                )
        );

        return map;
    }

    private static List<String> buildImports(JmmNode program) {
        return program
                .getChildren("ImportDecl")
                .stream()
                .map(each_import -> each_import.get("value"))
                .toList();
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> allVarDecl = new ArrayList<>();

        classDecl
                .getChildren("VarDecl")
                .stream()
                .forEach(varDecl -> {
                    // Get the first child of the varDecl node, which is the type node
                    JmmNode typeNode = varDecl.getChildren().get(0);
                    // Get the declaration attribute of the type node
                    String typeDeclaration = typeNode.get("declaration");

                    // Check if the type is an array
                    boolean isArray = "Array".equals(typeNode.getKind());

                    // Create a new Symbol with the type and name of the field
                    Symbol fieldSymbol = new Symbol(
                            new Type(typeDeclaration, isArray),
                            varDecl.get("name")
                    );

                    allVarDecl.add(fieldSymbol);
                });

        return allVarDecl;
    }


    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl
                .getChildren(METHOD_DECL)
                .stream()
                .forEach(method -> {
                    List<Symbol> parameters = method
                            .getChildren()
                            .stream()
                            .filter(child -> "Param".equals(child.getKind())) // check kind
                            .map(param -> {
                                // Get the first child of the param node, which is the type node
                                JmmNode typeNode = param.getChildren().get(0);
                                // Get the declaration attribute of the type node
                                String typeDeclaration = typeNode.get("declaration");

                                // Create a new Symbol with the type and name of the param
                                return new Symbol(
                                        new Type(typeDeclaration, false),
                                        param.get("name")
                                );
                            })
                            .collect(Collectors.toList());

                    map.put(method.get("name"), parameters);
                });

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl
                .getChildren(METHOD_DECL)
                .stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        return classDecl
                .getChildren(METHOD_DECL)
                .stream()
                .map(method -> method.get("name"))
                .toList();
    }

    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);

        return methodDecl
                .getChildren(VAR_DECL)
                .stream()
                .map(varDecl -> {
                    JmmNode typeNode = varDecl.getChild(0);
                    String typeDeclaration = typeNode.get("declaration");
                    boolean isArray = "Array".equals(typeNode.getKind());
                    return new Symbol(new Type(typeDeclaration, isArray), varDecl.get("name"));
                })
                .toList();
    }
}

