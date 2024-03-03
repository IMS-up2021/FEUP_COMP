package pt.up.fe.comp2024.symboltable;

import java.util.*;
import java.util.stream.Collectors;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {

  public static JmmSymbolTable build(JmmNode root) {
    JmmNode classDecl = root;

    var imports = buildImports(root);
    System.out.println(imports);

    for (JmmNode child : root.getChildren()){
      if (child.getKind().equals("importDeclaration")){

      } else if ("ClassDecl".equals(child.getKind())) {
        classDecl = child;
      }
    }


    String className = classDecl.get("name");


    var methods = buildMethods(classDecl);
    var returnTypes = buildReturnTypes(classDecl);
    var params = buildParams(classDecl);
    var locals = buildLocals(classDecl);



    return new JmmSymbolTable(className, methods, imports,returnTypes, params, locals);
  }

  private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
    // TODO: Simple implementation that needs to be expanded

    Map<String, Type> map = new HashMap<>();

    classDecl
      .getChildren(METHOD_DECL)
      .stream()
      .forEach(method ->
        map.put(method.get("name"), new Type(TypeUtils.getIntTypeName(), false))
      );

    return map;
  }

 private static  List<String> buildImports(JmmNode program){
    return program.getChildren("ImportDeclaration").stream().map(each_import -> each_import.get("value")).toList();
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
    // TODO: Simple implementation that needs to be expanded

    var intType = new Type(TypeUtils.getIntTypeName(), false);

    return methodDecl
      .getChildren(VAR_DECL)
      .stream()
      .map(varDecl -> new Symbol(intType, varDecl.get("name")))
      .toList();
  }
}
