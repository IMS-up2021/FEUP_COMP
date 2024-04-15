package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.specs.comp.ollir.ElementType.*;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(CallInstruction.class, this::generateCall);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class public ").append(className).append(NL).append(NL);

        // TODO: Hardcoded to Object, needs to be expanded
        //code.append(".super java/lang/Object").append(NL);

        // Determine superclass
        var superClass = classUnit.getSuperClass();

        if (superClass != null) {
            code.append(".super ").append(superClass).append(NL);
        } else {
            // If superclass is not explicitly defined, fallback to java/lang/Object
            code.append(".super java/lang/Object").append(NL);
        }



        for (var field : classUnit.getFields()){

            String name = field.getFieldName();
            String access = getFieldAccess(field);
            String type = getType(field.getFieldType().getTypeOfElement());
            code.append(".field " + access + name + " " + type + "\n");

        }




        // generate a single constructor method
        var defaultConstructor1 = """
                ;default constructor
                .method public <init>()V
                    aload_0
                """;

        String constructorBetween = "\tinvokespecial " + (superClass == null ? "java/lang/Object/<init>()V\n" : superClass + "/<init>()V\n");

        var defaultConstructor2 = """
                    return
                .end method
                """;

        code.append(defaultConstructor1 + constructorBetween + defaultConstructor2);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        var methodName = method.getMethodName();

        // TODO: Hardcoded param types and return type, needs to be expanded
        //code.append("\n.method ").append(modifier).append(methodName).append("(I)I").append(NL);

        if (method.isStaticMethod()) {code.append("\n.method ").append(modifier).append("static ").append(methodName).append("(");}
        else {code.append("\n.method ").append(modifier).append(methodName).append("(");}


        for (Element argument : method.getParams())
            code.append(getType(argument.getType().getTypeOfElement()));
        code.append(")" + getType(method.getReturnType().getTypeOfElement()) + "\n");

        /*
        // Dynamically generate parameter types
        var paramTypes = method.getParams().stream()
                .map(param -> param.getType().getTypeOfElement());

        // Dynamically generate return type
        var returnType = method.getReturnType();

        code.append("\n.method ").append(modifier).append(methodName)
                .append("(").append(paramTypes).append(")").append(returnType).append(NL);
    */


        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            System.out.println(inst.getInstType());
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }



        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }







    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();


        // generate code for loading what's on the right
        //code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        /*
        for (var t : currentMethod.getVarTable().values()){
            System.out.println(t.getScope());
            System.out.println(t.getVirtualReg());
            System.out.println(t.getVarType());
        }

         */





        // get register
        //var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        var reg = currentMethod.getVarTable();

        // (Binary) Operation assigment with increment
        if (assign.getRhs().getInstType() == InstructionType.BINARYOPER) {
            BinaryOpInstruction binaryInstruction = (BinaryOpInstruction) assign.getRhs();
            if (binaryInstruction.getOperation().getOpType() == OperationType.ADD) {

                Element leftOperand =  binaryInstruction.getLeftOperand();
                Element rightOperand = binaryInstruction.getRightOperand();
                Operand true_operand = null;
                LiteralElement true_element = null;

                if (leftOperand.isLiteral() && !rightOperand.isLiteral()) {
                    true_operand = (Operand) binaryInstruction.getRightOperand();
                    true_element = (LiteralElement) binaryInstruction.getLeftOperand();
                } else if (!leftOperand.isLiteral() && rightOperand.isLiteral()) {
                    true_operand = (Operand) binaryInstruction.getLeftOperand();
                    true_element = (LiteralElement) binaryInstruction.getRightOperand();
                }

                if (true_operand != null && true_element != null && operand.getName().equals(assign.getDest()) &&
                        this.between(Integer.parseInt(true_element.getLiteral()), -128, 127)) {
                    return "\tiinc " + reg.get(operand.getName()).getVirtualReg() + " " + Integer.parseInt(true_element.getLiteral()) + "\n";
                }
            }
        }


        code.append(generators.apply(assign.getRhs()));

        /*
        types of instructions
                INT32,
                BOOLEAN,
                ARRAYREF,
                OBJECTREF,
                CLASS,
                THIS,
                STRING,
                VOID;

         */
        if (assign.getTypeOfAssign().getTypeOfElement() != OBJECTREF)
            code.append(storeVariable(operand));


        // TODO: Hardcoded for int type, needs to be expanded
        //code.append("istore ").append(reg).append(NL);

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        return "iload " + reg + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case SUB -> "isub";
            case DIV -> "idiv";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        // TODO: Hardcoded to int return type, needs to be expanded
        //code.append(generators.apply(returnInst.getOperand()));
        //code.append("ireturn").append(NL);



        if (returnInst.hasReturnValue())
            code.append(generators.apply(returnInst.getOperand()));



        ElementType returnType = returnInst.getElementType();
        //ElementType returnType = operand.getType().getTypeOfElement();

        //code.append(generators.apply(returnInst.getOperand()));
        switch (returnType) {
            case INT32 -> code.append("ireturn");
            case BOOLEAN -> code.append("ireturn");
            case VOID -> code.append("return");
            case STRING -> code.append("Ljava/lang/String;");
            default -> {}
        };


        return code.toString();
    }


    private String generatePutField(FieldInstruction fieldInstruction){
        var code = new StringBuilder();
        // TODO: Implement this method

        //var reg = currentMethod.getVarTable();


        Element first = fieldInstruction.getOperands().get(0);  //operand: Test
        Element second = fieldInstruction.getOperands().get(1);  //operand: intField
        Element third = fieldInstruction.getOperands().get(2);  //literalelement 10

        // Carrega a referência "this" na pilha
        code.append("\taload_0").append(NL);

        // Carrega a constante 10 na pilha
        var reg = this.currentMethod.getVarTable();


        //code.append(reg.get(third.toElement()).getVirtualReg());

        //code.append("\tldc ").append(reg).append(NL);

        if (third.isLiteral()) {
            // Obtém o valor literal do terceiro operando
            String literalValue = ((LiteralElement) third).getLiteral();
            // Converte o valor literal para o tipo apropriado
            String valueType = getType(third.getType().getTypeOfElement());

            // Carrega o valor literal na pilha
            code.append("\tldc ").append(literalValue).append(NL);
        }



        //var className = ollirResult.getOllirClass().getClassName();
        //code.append(".class public ").append(className).append(NL).append(NL);

        var className = "";
        if (((Operand) first).getName() == "this") {
            className = ollirResult.getOllirClass().getClassName();
        } else {
            //imported
            className = "";
        }



        code.append("\tputfield ").append(className).append("/").append(((Operand) second).getName())
                .append(" ").append(getType(third.getType().getTypeOfElement()));


        //code.append(generateLiteral((LiteralElement) third));

        //var reg = currentMethod.getVarTable().get(second).getVirtualReg();



        //code.append(first); //operand: test
        //code.append(second); //operand: intField
        //code.append(third); //literalelement 10




        //Operand: this.THIS(Test)Operand: intField.INT32LiteralElement: 10.INT32


        //reg.put()

        //"\t" + loadVariable(table, first) + this.loadVariable(table, third) +
        //"\tputfield " + this.getImportedClassName(((Operand) first).getName()) +
        //"/" + ((Operand) second).getName() +  " " + this.getFieldType(second.getType()) + "\n";

        /*
        String name = field.getFieldName();
        String access = getFieldAccess(field);
        String type = getType(field.getFieldType().getTypeOfElement());
        code.append(".field " + access + name + " " + type + "\n");
        */



        return code.toString();
    }



    private String generateGetField(GetFieldInstruction instruction) {
        // TODO: Implement this method
        var code = new StringBuilder();

        //var reg = currentMethod.getVarTable();

        Element first = instruction.getOperands().get(0);  //operand: Test
        Element second = instruction.getOperands().get(1);  //operand: intField

        code.append("\taload_0").append(NL);

        var className = "";
        if (((Operand) first).getName() == "this") {
            className = ollirResult.getOllirClass().getClassName();
        } else {
            //imported
            className = "";
        }


        code.append("\tgetfield ").append(className).append("/").append(((Operand) second).getName())
                .append(" ").append(getType(second.getType().getTypeOfElement())).append("\n");

        return code.toString();
    }



    private String generateCall(CallInstruction instruction) {
        var code = new StringBuilder();

        var table = currentMethod.getVarTable();

        //instruction.getArguments().stream().forEach(System.out::println);
        CallType invocationType = instruction.getInvocationType();

        // Handle different types of calls
        switch (invocationType) {
            case invokevirtual:
                // Generate bytecode for virtual call
                code.append(generateVirtualCall(instruction));
                break;
            case invokespecial:
                // Generate bytecode for special call (e.g., constructor)
                code.append(generateSpecialCall(instruction));
                break;
            case invokestatic:
                // Generate bytecode for static call
                code.append(generateStaticCall(instruction));
                break;
            default:
                // Handle other types of calls if needed
                // Currently not handling other types of calls
                break;
        }



        // TODO: Implement this method
        return code.toString();


    }



    private String getType(ElementType type) {
        return switch (type) {
            case INT32 -> "I";
            case BOOLEAN -> "Z";
            case VOID -> "V";
            case STRING -> "[Ljava/lang/String;";
            default -> "[Ljava/lang/String;";
        };
    }


    private String getFieldAccess(Field field) {
        String access = "";
        if (field.getFieldAccessModifier() != AccessModifier.DEFAULT) {
            switch (field.getFieldAccessModifier()) {
                case PUBLIC -> access += "public ";
                case PROTECTED -> access += "protected ";
                case PRIVATE -> access += "private ";
            }
        }
        if (field.isFinalField()) access += "final ";
        if (field.isStaticField()) access += "static ";
        return access;
    }


    private boolean between(int value, int lower, int upper) {
        return value <= upper && value >= lower;
    }


    private String storeVariable(Operand operand) {

        var reg = currentMethod.getVarTable();

        return switch (operand.getType().getTypeOfElement()) {
            case ARRAYREF, STRING, THIS, OBJECTREF -> "\tastore" + this.getVariableIndex(operand.getName()) + "\n";
            case BOOLEAN, INT32 ->
                    reg.get(operand.getName()).getVarType().getTypeOfElement() == ElementType.ARRAYREF ?
                            "\tiastore\n" :
                            "\tistore" + this.getVariableIndex(operand.getName()) + "\n";
            default -> null;
        };
    }


    private String getVariableIndex(String variableName) {

        var reg = currentMethod.getVarTable();

        if (variableName.equals("this")) return "_0";
        int number = reg.get(variableName).getVirtualReg();
        return (number < 4 ? "_" : " ") + number;
    }


    private String generateSpecialCall(CallInstruction instruction) {

        var code = new StringBuilder();

        // Check if it's invoking a constructor
        if (instruction.getMethodName().toString().equals("<init>")) {
            // Generate bytecode to create a new instance of the class
            code.append("\tnew ").append(instruction.getCaller()).append(NL);
            code.append("\tdup").append(NL);
            // Generate bytecode to invoke constructor
            code.append("\tinvokespecial ").append(instruction.getCaller()).append("/<init>()V").append(NL);
        }
        return code.toString();
    }


    private String generateVirtualCall(CallInstruction instruction) {

        var code = new StringBuilder();

        return code.toString();
    }


    private String generateStaticCall(CallInstruction instruction) {

        var code = new StringBuilder();

        return code.toString();
    }


    /*
    private String loadVariable(Map<String, Descriptor> table, Element element) {
        if (element instanceof LiteralElement) return this.loadLiteralVariable(table, (LiteralElement) element) + "\n";
        if (element instanceof ArrayOperand) return this.loadArrayVariable(table, (ArrayOperand) element) + "\n";
        if (element instanceof Operand) return this.loadOperandVariable(table, (Operand) element) + "\n";
        return null;
    }
     */





}
