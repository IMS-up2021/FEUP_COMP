package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.sun.source.tree.Tree.Kind.ARRAY_ACCESS;
import static pt.up.fe.comp2024.ast.TypeUtils.*;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.BINARY_EXPR, this::checkBinaryExpression);
        addVisit(Kind.UNARY_EXPR, this::checkUnaryExpr);
        addVisit(Kind.ARRAY_ACCESS, this::checkArrayAccess);
        addVisit(Kind.ASSIGN_STMT, this::checkAssignment);
        addVisit(Kind.IF_ELSE_STMT, this::visitIfElseStmt);
        addVisit(Kind.METHOD_CALL, this::checkMethodCall);
    }


    // Verifica se o método chamado está declarado, se a quantidade e tipos dos argumentos são compatíveis
    // com os parâmetros esperados, e se a chamada é feita em um alvo válido.
    private Void checkMethodCall(JmmNode methodCall, SymbolTable table) {
        // Retrieve the method name
        String methodName = methodCall.get("method");
        if (methodName == null) {
            addError("Method name attribute is missing in MethodCall node", methodCall);
            return null;
        }

        JmmNode target = methodCall.getChild(0);

        // Get the type of the target
        Type targetType = getVarExprType(target, table);
        if (targetType == null) {
            addError("Variable is undeclared", methodCall);
            return null;
        }

        // Check if the target type is in the imports
        if (isImportedClass(targetType.getName(), table)) {
            return null;
        }

        // Check if the target type is the class or superclass
        String superClass = table.getSuper();
        if (superClass != null && (targetType.getName().equals(table.getClassName()) || targetType.getName().equals(superClass))) {
            return null;
        }

        // Retrieve the method from the symbol table
        var method = table.getMethods()
                .stream()
                .filter(m -> m.equals(methodName))
                .findFirst()
                .orElse(null);

        if (method == null) {
            addError("Method not found", methodCall);
            return null;
        }

        // Retrieve the list of parameters from the method
        var parameters = table.getParameters(methodName);

        // Check if the parameters list is empty
        if (parameters.isEmpty()) {
            addError("Method has no parameters defined: " + methodName, methodCall);
            return null;
        }

        // Retrieve the list of arguments from the method call
        var arguments = methodCall.getChildren();

        // Get the last parameter which is a symbol, if available
        var lastParam = parameters.get(parameters.size() - 1);

        // Check that the number of arguments matches the number of parameters
        boolean isVararg = Objects.equals(lastParam.getType().getName(), "Varargs");
        if (arguments.size() != parameters.size() && !isVararg) {
            addError("Number of arguments does not match number of parameters for method: " + methodName, methodCall);
            return null;
        }

        // For each argument, retrieve its type and compare it with the corresponding parameter's type
        if (!isVararg) {
            for (int i = 0; i < arguments.size(); i++) {
                var argument = arguments.get(i);
                var parameter = parameters.get(i);

                var argumentType = getVarExprType(argument, table);
                var parameterType = parameter.getType();

                if (!argumentType.getName().equals(parameterType.getName())) {
                    addError("Type of argument does not match type of parameter for method: " + methodName, methodCall);
                    return null;
                }
            }
        }

        return null;
    }


    // Armazena o nome do método atual e verifica se há parâmetros varargs que não estão na última posição.
    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");

        // Get the parameters of the method
        List<JmmNode> parameters = method.getChildren().stream()
                .filter(child -> "Param".equals(child.getKind()))
                .toList();

        // Check if any parameter after varargs
        for (int i = 0; i < parameters.size(); i++) {
            if ("Varargs".equals(parameters.get(i).getChild(0).getKind()) && i != parameters.size() - 1) {
                addError("Varargs parameter should be the last parameter", method);
            }
        }

        return null;
    }

    // Verifica se a variável está declarada localmente, como campo da classe, como parâmetro do método atual,
    // ou se é uma classe importada. Caso contrário, regista um erro.
    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        String varRefName = varRefExpr.get("name");

        // Check if variable is declared in current method
        if (!isDeclaredLocally(varRefName, table)) {
            // Check if variable is a field of the class
            if (!isField(varRefName, table)) {
                // Check if variable is a parameter of the current method
                if (!isParameter(varRefName, table)) {
                    // Check if variable is an imported class
                    if (!isImportedClass(varRefName, table)) {
                        // Variable is undeclared
                        addUndeclaredVariableError(varRefExpr, varRefName);
                    }
                }
            }
        }

        return null;
    }

    private boolean isDeclaredLocally(String varName, SymbolTable table) {
        return table.getLocalVariables(currentMethod).stream()
                .anyMatch(varDecl -> varDecl.getName().equals(varName));
    }

    private boolean isField(String varName, SymbolTable table) {
        return table.getFields().stream()
                .anyMatch(field -> field.getName().equals(varName));
    }

    private boolean isParameter(String varName, SymbolTable table) {
        return table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varName));
    }

    private boolean isImportedClass(String varName, SymbolTable table) {
        return table.getImports().stream()
                .anyMatch(imported -> (imported.endsWith(", " + varName + "]") || imported.equals("[" + varName + "]")));
    }

    // Tenta obter o tipo da expressão binária e registra um erro caso haja uma incompatibilidade de tipos.
    private Void checkBinaryExpression(JmmNode binaryExpr, SymbolTable table) {
        try {
            Type resultType = getBinExprType(binaryExpr, table);
        } catch (RuntimeException e) {
            addError(e.getMessage(), binaryExpr);
        }

        return null;
    }

    // Tenta obter o tipo da expressão unária e regista um erro caso haja uma incompatibilidade de tipos.
    private Void checkUnaryExpr(JmmNode exprNode, SymbolTable table) {
        try {
            Type resultType = getUnaryExprType(exprNode, table);
        } catch (RuntimeException e) {
            addError(e.getMessage(), exprNode);
        }
        return null;
    }

    // Cria um relatório de erro semântico com a linha, coluna e mensagem de erro, e o adiciona aos relatórios.
    private void addError(String message, JmmNode node) {
        int line = NodeUtils.getLine(node);
        int column = NodeUtils.getColumn(node);
        Report errorReport = Report.newError(Stage.SEMANTIC, line, column, message, null);
        addReport(errorReport);
    }


    private void addUndeclaredVariableError(JmmNode varRefExpr, String varName) {
        var message = String.format("Variable '%s' is undeclared.", varName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varRefExpr),
                NodeUtils.getColumn(varRefExpr),
                message,
                null)
        );
    }

    // Verifica se o índice é do tipo int e se a expressão de array é de um tipo array, registando erros conforme necessário.
    private Void checkArrayAccess(JmmNode arrayAccessNode, SymbolTable table) {
        JmmNode arrayExpr = arrayAccessNode.getChildren().get(0);
        JmmNode indexExpr = arrayAccessNode.getChildren().get(1);
        Type arrayType = getVarExprType(arrayExpr, table);
        if (!arrayType.isArray()) {
            addError("Array access is done over a non-array type", arrayAccessNode);
        }
        Type indexType = getVarExprType(indexExpr, table);
        if (!"int".equals(indexType.getName())) {
            addError("Array access index is not of integer type", indexExpr);
        }
        return null;
    }

    // Verifica se os tipos são compatíveis e se as regras de atribuição são respeitadas (incluindo casos de arrays).
    private Void checkAssignment(JmmNode node, SymbolTable table) {
        JmmNode leftOperand = node.getChildren().get(0);
        Type leftType = getVarExprType(leftOperand, table);

        JmmNode rightOperand = node.getChildren().get(1);
        Type rightType = getVarExprType(rightOperand, table);



        //arraycase
        if ((!leftType.isArray() && rightType.isArray()) || (leftType.isArray() && !rightType.isArray())) {
            addError("Array is incorrectly assigned", node);
        }

        if (leftType.isArray() && rightType.isArray()) {
            boolean flag = true;
            if (leftType.getName().equals("int")) {
                flag = rightOperand.getChildren().stream()
                        .allMatch(arrElement -> arrElement.getKind().equals("IntegerLiteral"));
            }
            if (leftType.getName().equals("boolean")) {
                flag = rightOperand.getChildren().stream()
                        .allMatch(arrElement -> arrElement.getKind().equals("TrueLiteral") || arrElement.getKind().equals("FalseLiteral"));
            }


            if (flag == false) {
                addError("Array is incorrectly assigned with diferent type", node);
            }
        }


        Type helperType = new Type(table.getSuper(), false);

        boolean isLeftAssignableToSuper = areTypesAssignable(leftType, helperType);
        boolean isRightAssignableToSuper = areTypesAssignable(rightType, helperType);
        boolean isLeftAssignableToRight = isAssignable(leftType, rightType);
        boolean bothImported = isImportedClass(leftType.getName(), table) && isImportedClass(rightType.getName(), table);
        if (!(isLeftAssignableToSuper || isRightAssignableToSuper || isLeftAssignableToRight || bothImported)) {
            addError("Type of the assignee is not compatible with the assigned or the superclass", node);
        }

        return null;
    }


    private boolean isAssignable(Type leftType, Type rightType) {
        // If either type is null, assignment is not possible
        if (leftType == null || rightType == null) {
            return false;
        }
        // Use the areTypesAssignable function to check whether types are assignable
        return areTypesAssignable(rightType, leftType);
    }

    // Verifica se a condição é uma expressão booleana e registra um erro se não for.
    //need to be recursive as well
    private Void visitIfElseStmt(JmmNode node, SymbolTable table) {
        JmmNode Condition = node.getChildren().get(0);
        System.out.println(Condition.get("op")); //BinaryExpr (op: +)

        if (Condition.get("op").equals('+') || Condition.get("op").equals('-')
                || Condition.get("op").equals('/') || Condition.get("op").equals('*')) {
            addError("If condition doesn't support non boolean operations", Condition);
        }


        JmmNode leftOp = Condition.getChild(0);
        JmmNode rightOp = Condition.getChild(1);

        System.out.println(leftOp.getKind().equals("IntegerLiteral"));


        if (leftOp.getKind().equals("TrueLiteral") || rightOp.getKind().equals("FalseLiteral")) {
        } else {
            addError("Type of the condition is not boolean", Condition);
        }


        return null;
    }

}