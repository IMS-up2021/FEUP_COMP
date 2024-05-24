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
        addVisit(Kind.BRACKET_EXPR, this::checkBracketExpr);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
    }

    private Type getVariableType(String varName, SymbolTable symbolTable, String currentMethod) {
        for (var var : symbolTable.getLocalVariables(currentMethod)) {
            if (var.getName().equals(varName)) {
                return var.getType();
            }
        }

        for (var field : symbolTable.getFields()) {
            if (field.getName().equals(varName)) {
                return field.getType();
            }
        }

        for (var param : symbolTable.getParameters(currentMethod)) {
            if (param.getName().equals(varName)) {
                return param.getType();
            }
        }

        return null;
    }

    private Void visitWhileStmt(JmmNode jmmNode, SymbolTable symbolTable) {
        JmmNode condition = jmmNode.getChildren().get(0);

        if (condition.getKind().equals("VarRefExpr")) {
            visitVarRefExpr(condition, symbolTable);

            // Get the variable name
            String varName = condition.get("name");

            // Get the type of the variable from the symbol table
            Type varType = getVariableType(varName, symbolTable, currentMethod);

            // Check if the variable type is boolean
            if (!varType.getName().equals("boolean")) {
                // If the variable type is not boolean, add an error
                addError("The condition of the while statement is not a boolean", condition);
            }
        }
        return null;
    }

    private Void checkBracketExpr(JmmNode jmmNode, SymbolTable symbolTable) {
        JmmNode firstChild = jmmNode.getChildren().get(0);
        Type firstType = getExprType(firstChild, symbolTable);
        Type secondType = getExprType(jmmNode.getChildren().get(1), symbolTable);
        if (!firstType.isArray()) {
            addError("Array access is done over a non-array type", jmmNode);
        }
        if (!secondType.getName().equals("int")) {
            addError("Array access index is not of integer type", jmmNode);
        }
        return null;
    }


    private Void checkMethodCall(JmmNode methodCall, SymbolTable table) {
        String methodName = methodCall.get("method");
        if (methodName == null) {
            addError("Method name attribute is missing in MethodCall node", methodCall);
            return null;
        }

        JmmNode target = methodCall.getChild(0);

        // Get the type of the target
        Type targetType = null;
        try {
            targetType = getExprType(target, table);
        } catch (Exception e) {
            // Handle the exception
            System.err.println("An error occurred while getting the variable expression type: " + e.getMessage());
        }
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

        var parameters = table.getParameters(methodName);


        var children = methodCall.getChildren();

        int arguments = children.size() - 1;

        if (!parameters.isEmpty()) {
            var lastParam = parameters.get(parameters.size() - 1);
            // Check that the number of arguments matches the number of parameters
            boolean isVararg = lastParam.getType().getName().endsWith("V");
            if (arguments != parameters.size() && !isVararg) {
                addError("Number of arguments does not match number of parameters for method: " + methodName, methodCall);
                return null;
            }
            // For each argument, retrieve its type and compare it with the corresponding parameter's type
            if (!isVararg) {
                for (int i = 0; i < arguments; i++) {
                    var argument = children.get(i + 1);
                    var parameter = parameters.get(i);

                    var argumentType = getExprType(argument, table);
                    var parameterType = parameter.getType();

                    if (!argumentType.getName().equals(parameterType.getName())) {
                        addError("Type of argument does not match type of parameter for method: " + methodName, methodCall);
                        return null;
                    }
                }
            }
        }

        return null;
    }

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

        // Get the return type of the method from the symbol table
        Type returnType = table.getReturnType(currentMethod);

        for (JmmNode child : method.getChildren()) {
            visit(child, table);
        }

        if (!method.getChildren().isEmpty()) {
            // Get the last child of the method node
            JmmNode lastChild = method.getChildren().get(method.getChildren().size() - 1);

            // Get the type of the last child
            Type lastChildType = null;
            try {
                lastChildType = getExprType(lastChild, table);
            } catch (Exception e) {
                addError("An error occurred while getting the expression type: " + e.getMessage(), lastChild);
                return null;
            }

            if (Objects.equals(lastChildType.getName(), "INVALIDBINOP")) {
                addError("Invalid type for binary operation!", method);
            }
            if (Objects.equals(lastChildType.getName(), "INVALIDARRACC")) {
                addError("Invalid array access!", method);
            }

            // Check if the return type matches the type of the last child
            if (!areTypesAssignable(lastChildType, returnType)) {
                addError("Return type does not match the type of the returned expression", method);
            }

            // Check if the isArray property matches
            if (lastChildType.isArray() != returnType.isArray()) {
                addError("Return type array property does not match the array property of the returned expression", method);
            }
        }

        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        String varRefName = varRefExpr.get("name");

        if (!isDeclaredLocally(varRefName, table)) {
            if (!isField(varRefName, table)) {
                if (!isParameter(varRefName, table)) {
                    if (!isImportedClass(varRefName, table)) {
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

    private Void checkBinaryExpression(JmmNode binaryExpr, SymbolTable table) {
        try {
            Type resultType = getBinExprType(binaryExpr, table);
        } catch (RuntimeException e) {
            addError(e.getMessage(), binaryExpr);
        }

        return null;
    }

    private Void checkUnaryExpr(JmmNode exprNode, SymbolTable table) {
        try {
            Type resultType = getUnaryExprType(exprNode, table);
        } catch (RuntimeException e) {
            addError(e.getMessage(), exprNode);
        }
        return null;
    }

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

    private Void checkArrayAccess(JmmNode arrayAccessNode, SymbolTable table) {
        JmmNode arrayExpr = arrayAccessNode.getChildren().get(0);
        JmmNode indexExpr = arrayAccessNode.getChildren().get(1);
        Type arrayType = getExprType(arrayExpr, table);
        if (!arrayType.isArray()) {
            addError("Array access is done over a non-array type", arrayAccessNode);
        }
        Type indexType = getExprType(indexExpr, table);
        if (!"int".equals(indexType.getName())) {
            addError("Array access index is not of integer type", indexExpr);
        }
        return null;
    }

    private Void checkAssignment(JmmNode node, SymbolTable table) {
        JmmNode leftOperand = node.getChildren().get(0);
        Type leftType = getExprType(leftOperand, table);

        JmmNode rightOperand = node.getChildren().get(1);
        Type rightType = getExprType(rightOperand, table);


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
        return areTypesAssignable(rightType, leftType);
    }

    //need to be recursive as well
    private Void visitIfElseStmt(JmmNode node, SymbolTable table) {
        JmmNode Condition = node.getChildren().get(0);

        if (Condition.get("op").equals("+") || Condition.get("op").equals("-")
                || Condition.get("op").equals("/") || Condition.get("op").equals("*")) {
            addError("If condition doesn't support non boolean operations", Condition);
        }


        JmmNode leftOp = Condition.getChild(0);
        JmmNode rightOp = Condition.getChild(1);


        if (leftOp.getKind().equals("TrueLiteral") || rightOp.getKind().equals("FalseLiteral")) {
        } else {
            addError("Type of the condition is not boolean", Condition);
        }


        return null;
    }

}