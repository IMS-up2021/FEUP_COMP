package pt.up.fe.comp2024.symboltable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

public class JmmSymbolTable implements SymbolTable {

  private final String className;

  private final String superClassName;
  
  private final List<String> methods;

  private final List<String> imports;

  private final List<Symbol> fields;

  private final Map<String, Type> returnTypes;
  private final Map<String, List<Symbol>> params;
  private final Map<String, List<Symbol>> locals;

  public JmmSymbolTable(
    String className,
    String superClassName,
    List<String> methods,
    List<String> imports,
    List<Symbol> fields,
    Map<String, Type> returnTypes,
    Map<String, List<Symbol>> params,
    Map<String, List<Symbol>> locals
  ) {
    this.className = className;
    this.superClassName = superClassName;
    this.methods = methods;
    this.imports = imports;
    this.fields = fields;
    this.returnTypes = returnTypes;
    this.params = params;
    this.locals = locals;
  }

  @Override
  public List<String> getImports() {
    return imports;
  }

  @Override
  public String getClassName() {
    return className;
  }

  @Override
  public String getSuper() {
    return this.superClassName;
  }

  @Override
  public List<Symbol> getFields() {
    return fields;
  }

  @Override
  public List<String> getMethods() {
    return Collections.unmodifiableList(methods);
  }

@Override
public Type getReturnType(String methodName) {

    // If the method is "main", return null since it doesn't have a return type
    // if ("main".equals(methodName)) {
    //     System.out.println("The main method does not have a return type.");
    //     return null;
    // }

    // Debug print: print the method name and the return type
    Type returnType = this.returnTypes.get(methodName);

    // If returnType is null, print a message and return null
    if (returnType == null) {
        System.out.println("No return type found for method: " + methodName);
        return null;
    }

    return returnType;
}

  @Override
  public List<Symbol> getParameters(String methodSignature) {
    return Collections.unmodifiableList(params.get(methodSignature));
  }

  @Override
  public List<Symbol> getLocalVariables(String methodSignature) {
    return Collections.unmodifiableList(locals.get(methodSignature));
  }
}
