package pt.up.fe.comp2024.optimization;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.optimization.OptUtils.getTemp;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";

    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }

    @Override
    protected void buildVisitor() {
        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(EXTENDS_DECL, this::visitExtendsDecl);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(IMPORT_DECLARATION, this::visitImportDeclaration);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(BINARY_EXPR, this::visitBinaryExpr);
        addVisit(VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(SIMPLE_EXPR_STMT, this::visitSimpleExpr);

        addVisit(TYPE, this::defaultVisit);

        addVisit(UNARY_EXPR, this::defaultVisit);
        addVisit(INTEGER_LITERAL, this::defaultVisit);
        setDefaultVisit(this::defaultVisit);
    }

    private void helperMethod(JmmNode node, StringBuilder code, int i) {
        var child = node.getJmmChild(i);

        if (child.getKind().equals("MethodCall")) {
            var childCode = exprVisitor.visit(child);
            code.append(childCode.getComputation());
            code.append(childCode.getCode());
        } else if (child.getKind().equals("VarDecl")) {
            var childCode = visit(child);
            code.append(childCode);
        } else if (child.getKind().equals("ReturnStmt")) {
            var childCode = visit(child);
            code.append(childCode);
        } else {
            var childCode = visit(child);
            code.append(childCode).append("\n");
        }
    }

    //visit method for simple expr statements
    private String visitSimpleExpr(JmmNode node, Void unused) {
        // Get the child node
        JmmNode childNode = node.getJmmChild(0);

        // Visit the child node
        var result = exprVisitor.visit(childNode);

        // Return the result
        return result.getCode();
    }

    //visit method for import statements
    private String visitImportDeclaration(JmmNode node, Void unused) {
        String importedModule = node.get("ID");
        // Generate some OLLIR code for illustrative purposes
        String ollirCode = "import " + importedModule + END_STMT;
        return ollirCode;
    }

    //visit varefexpr statements
    private String visitVarRefExpr(JmmNode node, Void unused) {
        String varName = node.get("name");
        // Generate some OLLIR code for illustrative purposes
        // get type from symbol table
        Type type = TypeUtils.getExprType(node, table);
        // Convert the type to OLLIR format
        String ollirType = OptUtils.toOllirType(type);
        // Generate OLLIR code
        String ollirCode = varName + ollirType;
        return ollirCode;
    }

    //visit method for binary expressions
    private String visitBinaryExpr(JmmNode node, Void unused) {
        // Get the left and right children of the binary expression
        JmmNode leftChild = node.getJmmChild(0);
        JmmNode rightChild = node.getJmmChild(1);
        // Get the operator of the binary expression
        String operator = node.get("op");
        // get the current tmp from OptUtils
        String tmp = getTemp();
        // get type from symbol table
        Type type = TypeUtils.getExprType(node, table);
        // Convert the type to OLLIR format
        String ollirType = OptUtils.toOllirType(type);
        // Visit the left and right children
        String leftChildCode = visit(leftChild);
        String rightChildCode = visit(rightChild);
        // Generate OLLIR code
        String ollirCode = tmp + ollirType + " := " + ollirType + " " + leftChildCode + " " + operator + ollirType + " " + rightChildCode + ";";
        return ollirCode;
    }


    //visit method for extend statements
    private String visitExtendsDecl(JmmNode node, Void unused) {
        String extendedClass = node.get("name");
        // Generate some OLLIR code for illustrative purposes
        String ollirCode = "extends " + extendedClass + SPACE;
        return ollirCode;
    }

    //visit method for var decl statements
    private String visitVarDecl(JmmNode node, Void unused) {
        // Check the parent of the VAR_DECL node
        JmmNode parent = node.getParent();
        if (parent.getKind().equals("ClassDecl")) {
            String varName = node.get("name");
            // Get the type from the first child node's "declaration" attribute
            // Convert the type to OLLIR format
            String ollirType = OptUtils.toOllirType(node.getJmmChild(0));
            // Generate OLLIR code for the variable declaration
            String ollirCode = ".field public " + varName + ollirType + END_STMT;
            return ollirCode;
        }
        return "";
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        var leftNode = node.getJmmChild(0);
        Type type = TypeUtils.getExprType(leftNode, table);
        String ollirType = OptUtils.toOllirType(type);
        var rightNode = node.getJmmChild(1);
        var lhs = exprVisitor.visit(leftNode);
        var rhs = exprVisitor.visit(rightNode);
        String end = END_STMT;
        if (rightNode.getKind().equals("MethodCall")) {
            end = "";
        }
        String code = rhs.getComputation() + lhs.getCode() + ASSIGN + ollirType + SPACE + rhs.getCode() + end;
        if (rightNode.getKind().equals("NewObject")) {
            code = code + "invokespecial(" + leftNode.get("name") + "." + rightNode.get("name") + ", \"<init>\").V" + END_STMT;
        }
        return code;
    }

    private String visitReturn(JmmNode node, Void unused) {
        String methodName = node
                .getAncestor(METHOD_DECL)
                .map(method -> method.get("name"))
                .orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);
        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {
        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        return id + typeCode;
    }

    private String visitMethodDecl(JmmNode node, Void unused) {
        if ("main".equals(node.get("name"))) {
            //create string builder code
            StringBuilder code = new StringBuilder(".method public static main(args.array.String).V {\n");
            //add et.V ;
            //}
            for (int i = 0; i < node.getNumChildren(); i++) {
                helperMethod(node, code, i);
            }
            code.append("ret.V ;\n}");
            return code.toString();
        }

        StringBuilder code = new StringBuilder(".method ");


        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        // name
        var name = node.get("name");
        code.append(name);


        // param
        //only do next code if there are parameters
        code.append("(");
        var afterParam = 1;
        for (JmmNode child : node.getChildren()) {
            if (child.getKind().equals(Kind.PARAM.toString())) { // Check if the child node is a parameter
                var paramCode = visit(child);
                afterParam++;
                if (!code.toString().endsWith("(")) { // If it's not the first parameter, append a comma before it
                    code.append(", ");
                }
                code.append(paramCode);
            }
        }
        code.append(")");


        // type
        var retType = OptUtils.toOllirType(node.getJmmChild(0));
        code.append(retType);
        code.append(L_BRACKET);


        // rest of its children stmts

        for (int i = afterParam; i < node.getNumChildren() - 1; i++) {
            helperMethod(node, code, i);
        }

        JmmNode lastChild = node.getJmmChild(node.getNumChildren() - 1);
        String returnValue;

        // Check if the last child is a VarRefExpr or IntegerLiteral
        if (lastChild.getKind().equals("VarRefExpr")) {
            // A variable is being returned
            returnValue = lastChild.get("name");
        } else if (lastChild.getKind().equals("IntegerLiteral")) {
            // A literal is being returned
            returnValue = lastChild.get("value");
        } else if (lastChild.getKind().equals("BinaryExpr")) {
            // return the result of the binary expression, current tmp
            code.append(visit(lastChild));
            returnValue = OptUtils.getCurrentTemp();
        } else {
            throw new IllegalArgumentException("Unsupported return type: " + lastChild.getKind());
        }

        // Append the return statement to the OLLIR code
        code.append(NL);
        code.append("ret");
        code.append(retType);
        code.append(SPACE);
        code.append(returnValue);
        code.append(retType);
        code.append(END_STMT);


        code.append(R_BRACKET);
        code.append(NL);
        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());
        code.append(SPACE);
        int start = 0;
        if (EXTENDS_DECL.check(node.getJmmChild(0))) {
            code.append(visit(node.getJmmChild(0)));
            start = 1;
        }
        code.append(L_BRACKET);

        var needNl = true;

        for (int i = start; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }
        code.append(NL);
        code.append(NL);
        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {
        return (
                ".construct " +
                        table.getClassName() +
                        "().V {\n" +
                        "invokespecial(this, \"<init>\").V;\n" +
                        "}\n"
        );
    }

    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        node.getChildren().stream().map(this::visit).forEach(code::append);
        
        System.out.println(code);
        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
