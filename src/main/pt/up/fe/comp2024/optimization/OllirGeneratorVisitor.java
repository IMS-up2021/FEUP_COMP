package pt.up.fe.comp2024.optimization;

import static pt.up.fe.comp2024.ast.Kind.*;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

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

    //visit method for simple expr statements
    private String visitSimpleExpr(JmmNode node, Void unused) {
        return visit(node.getJmmChild(0));
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
        String tmp = OptUtils.getTemp();
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
        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);

        code.append(lhs.getCode());
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        System.out.println("ASSIGN OLLIR WILL BE PRINTED");
        System.out.println(code.toString());
        System.out.println("OLLIR PRINTED");
        return code.toString();
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
        System.out.println("RETURN OLLIR WILL BE PRINTED");
        System.out.println(code.toString());
        System.out.println("OLLIR PRINTED");
        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {
        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;
        System.out.println("PARAM OLLIR WILL BE PRINTED");
        System.out.println(code);
        System.out.println("OLLIR PRINTED");
        return code;
    }

    private String visitMethodDecl(JmmNode node, Void unused) {
        if ("main".equals(node.get("name"))) {
            return ".method public static main(args.array.String).V {\nret.V ;\n}";
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
            var child = node.getJmmChild(i);
            var childCode = visit(child);
            code.append(childCode);
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
        System.out.println("METHOD OLLIR WILL BE PRINTED");
        System.out.println(code.toString());
        System.out.println("OLLIR PRINTED");
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

        code.append(buildConstructor());
        code.append(R_BRACKET);

        System.out.println("CLASS OLLIR WILL BE PRINTED");
        System.out.println(code.toString());
        System.out.println("OLLIR PRINTED");
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
