package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.*;
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

    ClassUnit classUnit;


    int argsToPop;

    int IndexJUMP;


    int stackLimit;
    int currentStackSiz;




    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;
        classUnit = null;

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
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCondInstruction);
        generators.put(GotoInstruction.class, this::generateGotoInstruction);
        generators.put(CondBranchInstruction.class, this::generateCondBranchInstruction);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOpInstruction);


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
        this.classUnit = ollirResult.getOllirClass();
        code.append(".class public ").append(className).append(NL).append(NL);

        // TODO: Hardcoded to Object, needs to be expanded
        //code.append(".super java/lang/Object").append(NL);




        this.IndexJUMP = 0;



        // Determine superclass
        var superClass = classUnit.getSuperClass();

        if (superClass != null) {
            code.append(".super ").append(superClass).append(NL);
        } else {
            code.append(".super java/lang/Object").append(NL);
        }


        for (var field : classUnit.getFields()){

            String name = field.getFieldName();
            String access = getAccessFromField(field);
            String type = getTypeOfField(field.getFieldType());
            code.append(".field ").append(access).append(name + " ").append(type).append(NL);
        }


        var defaultConstructor1 = """
                ;default constructor
                .method public <init>()V
                    .limit stack 1
                    .limit locals 1
                    aload_0
                """;

        String constructorBetween = "\tinvokespecial " + (superClass == null ? "java/lang/Object/<init>()V\n" : superClass + "/<init>()V\n");

        var defaultConstructor2 = """
                    return
                .end method
                """;

        code.append(defaultConstructor1 + constructorBetween + defaultConstructor2);

        for (var method : ollirResult.getOllirClass().getMethods()) {

            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }









    private String generateMethod(Method method) {

        var localsLimit = 0;
        localsLimit = this.getLimitFromLocals(method);

        this.stackLimit = 0;
        this.currentStackSiz = 0;



        currentMethod = method;

        var header = new StringBuilder();


        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        var methodName = method.getMethodName();



        if (method.isStaticMethod()) {header.append("\n.method ").append(modifier).append("static ").append(methodName).append("(");}
        else {header.append("\n.method ").append(modifier).append(methodName).append("(");}


        for (Element argument : method.getParams()){
            header.append(getTypeOfField(argument.getType()));
        }
        header.append(")" + getTypeOfField(method.getReturnType()) + "\n");


        var code = new StringBuilder();

        //code.append("invokestatic ioPlus/printHelloWorld()V");


        String instructs = "";

        for (var inst : method.getInstructions()) {



            for (Map.Entry<String, Instruction> label : method.getLabels().entrySet()) {
                if (label.getValue().equals(inst)) {
                    instructs += TAB + label.getKey() + ":" + NL;
                }
            }

            code.append(instructs);
            instructs = "";

            System.out.println(inst.getInstType());
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));


            var tipo = inst.getInstType();
            if (tipo.equals(InstructionType.CALL) &&
                    ((CallInstruction) inst).getReturnType().getTypeOfElement() != ElementType.VOID) {
                instructs += TAB + "pop";
                instructs +=NL;
                this.popS(1);
            }

            code.append(instCode);
        }


        code.append(".end method\n");

        var limits = new StringBuilder();


        limits.append(TAB).append(".limit stack ").append(this.stackLimit+2).append(NL);
        limits.append(TAB).append(".limit locals ").append(localsLimit).append(NL);


        header.append(limits).append(code);



        currentMethod = null;

        return header.toString();
    }



    /*
    ASSIGN,
    CALL,
    GOTO,
    BRANCH,
    RETURN,
    PUTFIELD,
    GETFIELD,
    UNARYOPER,
    BINARYOPER,
    NOPER;
     */


    private HashMap<String, Descriptor> getvarTable(){
        return this.currentMethod.getVarTable();
    }




    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();


        var varTable = getvarTable();



        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;


        var rhs = assign.getRhs();



        if (lhs instanceof ArrayOperand) {
            return this.generateArrayAssignment(assign);
        }






        var reg = getvarTable();

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
                        isBetween(Integer.parseInt(true_element.getLiteral()), -128, 127)) {
                    return "\tiinc " + reg.get(operand.getName()).getVirtualReg() + " " + Integer.parseInt(true_element.getLiteral()) + "\n";
                }
            }





        }



        code.append(getInst(assign.getRhs()));


        code.append(storeVar((Operand) assign.getDest()));





        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        var code = new StringBuilder();

        code.append(generators.apply(singleOp.getSingleOperand()));

        return code.toString();
    }

    private String generateLiteral(LiteralElement literal) {
        var code = new StringBuilder();

        code.append("ldc " + literal.getLiteral() + NL);

        return code.toString();
    }



    private String generateOperand(Operand operand) {

        var code = new StringBuilder();

        var varTable = getvarTable();

        var reg = varTable.get(operand.getName()).getVirtualReg();

        if (isBetween(reg,1,1)) {code.append("iload_").append(reg).append(NL); return code.toString(); }   //not sure why

        code.append("iload ").append(reg).append(NL);
        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        var varTable = getvarTable();


        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case DIV -> "idiv";
            case MUL -> "imul";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);
        popS(1);

        return code.toString();
    }



    private String generatePutField(FieldInstruction fieldInstruction){
        var code = new StringBuilder();




        popS(2);


        var varTable = getvarTable();

        Element First_ = fieldInstruction.getOperands().get(0);  //operand: Test
        Element Second_ = fieldInstruction.getOperands().get(1);  //operand: intField
        Element Third_ = fieldInstruction.getOperands().get(2);  //literalelement 10


        code.append("\taload_0").append(NL);



        if (Third_.isLiteral()) {
            String literalValue = ((LiteralElement) Third_).getLiteral();

            String valueType = getElemType(Third_.getType().getTypeOfElement());

            code.append("ldc ").append(literalValue).append(NL);
        }



        var className = "";
        if (((Operand) First_).getName() == "this") {
            className = ollirResult.getOllirClass().getClassName();
        } else {
            //imported
            className = "";
        }


        var Code = new StringBuilder();

        Code.append(TAB).append(loadVar(First_))
                .append(loadVar(Third_)).append(TAB).append("putfield").append(" ")
                .append(getClassNameFromImports(((Operand) First_).getName())).append("/")
                .append(((Operand) Second_).getName()).append(" ").append(getTypeOfField(Second_.getType()))
                .append(NL);

        return Code.toString();

        //return code.toString();
    }



    private String generateGetField(GetFieldInstruction getFieldinst) {
        // TODO: Implement this method
        var code = new StringBuilder();

        var varTable = getvarTable();

        Element first = getFieldinst.getOperands().get(0);  //operand: Test
        Element second = getFieldinst.getOperands().get(1);  //operand: intField

        code.append("\taload_0").append(NL);

        var className = "";
        if (((Operand) first).getName() == "this") {
            className = ollirResult.getOllirClass().getClassName();
        } else {
            //imported
            className = "";
        }


        code.append("\tgetfield ").append(className).append("/").append(((Operand) second).getName())
                .append(" ").append(getElemType(second.getType().getTypeOfElement())).append("\n");

        return code.toString();
    }



    private String generateCall(CallInstruction callInst) {

        var code = new StringBuilder();


        this.argsToPop = 0;

        var varTable = getvarTable();

        CallType invocationType = callInst.getInvocationType();

        // Handle different types of calls
        switch (invocationType) {
            case invokevirtual:
                // Generate bytecode for virtual call
                code.append(generateVirtualCall(callInst));
                break;
            case invokespecial:
                // Generate bytecode for special call (e.g., constructor)
                code.append(generateSpecialCall(callInst));
                break;
            case invokestatic:
                // Generate bytecode for static call
                code.append(generateStaticCall(callInst));
                break;
            case ldc:
                code.append(generateLdcCall(callInst));
                break;
            case arraylength:
                code.append(generateLengthCall(callInst));
                break;
            case NEW:
                if (callInst.getReturnType().getTypeOfElement() == OBJECTREF){
                    code.append(generateObjectCall(callInst));
                    //code.append("\tdup\n");
                } else {
                    code.append(generateArrayCall(callInst));
                    //code.append("\tdup\n");
                }
                break;



            default:
                // Handle other types of calls if needed
                // Currently not handling other types of calls
                break;

        }


        popS(argsToPop);



        // TODO: Implement this method
        return code.toString();


    }


    private String generateObjectCall(CallInstruction CallInst) {

        var operands = CallInst.getOperands();

        String object = "";
        var code = new StringBuilder();

        this.argsToPop = -1;

        //var varTable = getvarTable();


        for (Element Elem : CallInst.getArguments()) {
            this.argsToPop = this.argsToPop + 1;
            code.append(loadVar(Elem));
        }


        String nameOfClass = ((Operand) CallInst.getOperands().get(0)).getName();
        code.append(object).append(TAB)
                .append("new ")
                .append(getClassNameFromImports(nameOfClass))
                .append(NL);
        return code.toString();

    }

    private String getElemType(ElementType Type) {
        //var varTable = getvarTable();

        var tipo = new StringBuilder();

        switch (Type) {
            case INT32 -> {tipo.append("I"); return tipo.toString();}
            case BOOLEAN -> {tipo.append("Z"); return tipo.toString();}
            case STRING -> {tipo.append("Ljava/lang/String;"); return tipo.toString();}
            case VOID -> {tipo.append("V"); return tipo.toString();}
            default -> {return null;}
        }
    }



    private String getTypeOfField(Type type) {

        var fieldTipo = new StringBuilder();

        var helper = "";

        //var varTable = getvarTable();

        var typeOfElem = type.getTypeOfElement();
        switch (typeOfElem) {
            case OBJECTREF -> {
                var tipo = (ClassType) type;
                helper += "L" + this.getClassNameFromImports(tipo.getName()) + ";";
            }
            case ARRAYREF -> {
                var tipo = (ArrayType) type;
                helper += "[" + this.getElemType(tipo.getElementType().getTypeOfElement());
            }
            default -> helper += this.getElemType(type.getTypeOfElement());
        };


        fieldTipo.append(helper);
        return fieldTipo.toString();

    }




    private String getAccessFromField(Field field) {

        var code = new StringBuilder();

        //var varTable = getvarTable();

        if (!field.getFieldAccessModifier().equals(AccessModifier.DEFAULT)) {
            switch (field.getFieldAccessModifier()) {
                case PROTECTED -> {code.append("protected ");}
                case PRIVATE -> {code.append("private ");}
                case PUBLIC -> {code.append("public ");}
            }
        }
        if (field.isFinalField()) {
            code.append("final ");

        }
        if (field.isStaticField()) {
            code.append("static ");
        }
        return code.toString();
    }


    private boolean isBetween(int val, int low, int high) {
        //var varTable = getvarTable();

        if (val >= low && val <= high) return true;

        return false;
    }


    private String storeVar(Operand oper) {

        var code = new StringBuilder();

        var reg = getvarTable();

        var tipoElem = oper.getType().getTypeOfElement();

        switch (tipoElem) {
            case BOOLEAN, INT32 -> {

                if (reg.get(oper.getName()).getVarType().getTypeOfElement().equals(ElementType.ARRAYREF)) {
                    popS(3);
                } else popS(1);


                if (reg.get(oper.getName()).getVarType().getTypeOfElement().equals(ElementType.ARRAYREF)) {
                    code.append(TAB).append("iastore").append(NL);
                    return code.toString();
                } else {
                    code.append("istore").append(getVarIndex(oper.getName())).append(NL);
                    return code.toString();

                }

            }
            case ARRAYREF, STRING, THIS, OBJECTREF ->{
                popS(1);
                code.append(TAB).append("astore")
                        .append(getVarIndex(oper.getName())).append(NL);

                return code.toString();
            }


            default -> {return "";}
        }
    }





    private String generateSpecialCall(CallInstruction CallInst) {


        var varTable = getvarTable();


        var code = new StringBuilder();


        code.append(TAB).append(loadVar(CallInst.getOperands().get(0)))
                .append(TAB).append("invokespecial").append(" ");


        if (CallInst.getOperands().get(0).getType().getTypeOfElement().equals(THIS)){
            code.append(CallInst.getCaller().toString());
            code.append("/<init>");
        } else {
            code.append(getClassNameFromImports(((ClassType) CallInst.getOperands().get(0).getType()).getName()));
            code.append("/<init>");
        }




        code.append("(");

        for (Element elem : CallInst.getArguments())
            code.append(getTypeOfField(elem.getType()));


        code.append(")");



        Type typeRet = CallInst.getReturnType();


        if (typeRet.getTypeOfElement().equals(ElementType.VOID)){

        } else {
            this.argsToPop--;
        }


        code.append(getTypeOfField(typeRet))
                .append(NL);


        return code.toString();


    }






    private String generateVirtualCall(CallInstruction Callinst) {

        var varTable = getvarTable();

        var code = new StringBuilder();

        var operands = Callinst.getOperands();



        code.append(loadVar(Callinst.getOperands().get(0)));

        //second
        LiteralElement Second_ = (LiteralElement) Callinst.getOperands().get(1);

        //first
        Operand First_ = (Operand) Callinst.getOperands().get(0);


        argsToPop = 1;



        for (Element staticElement : Callinst.getArguments()) {
            code.append(loadVar(staticElement));
            argsToPop++;
        }

        code.append(TAB)
                .append("invokevirtual").append(" ")
                .append(getClassNameFromImports(((ClassType) First_.getType()).getName()))
                .append("/").append(Second_.getLiteral().replace("\"", ""));



        code.append("(");

        for (Element elem : Callinst.getArguments())
            code.append(getTypeOfField(elem.getType()));

        code.append(")");


        Type typeRet = Callinst.getReturnType();

        if (typeRet.getTypeOfElement().equals(ElementType.VOID)){

        } else {
            this.argsToPop--;
        }


        code.append(getTypeOfField(typeRet))
                .append(NL);



        return code.toString();
    }


    private String generateStaticCall(CallInstruction Callinst) {

        var code = new StringBuilder();

        var varTable = getvarTable();

        this.argsToPop = 0;


        for (Element staticElement : Callinst.getOperands()) {
            this.argsToPop++;
            code.append(loadVar(staticElement));
        }




        Operand first = (Operand) Callinst.getCaller();
        LiteralElement second = (LiteralElement) Callinst.getMethodName();
        code.append("invokestatic ").append(getClassNameFromImports(first.getName()))
                .append("/").append(second.getLiteral().replace("\"", ""));



        code.append("(");


        for (Element elem : Callinst.getArguments()) {
            code.append(getElemType(elem.getType().getTypeOfElement()));
        }

        code.append(")").append(getElemType(Callinst.getReturnType().getTypeOfElement())).append("\n");

        if (Callinst.getReturnType().getTypeOfElement().equals(ElementType.VOID)){

        } else {
            this.argsToPop--;
        }


        return code.toString();
    }




    private String loadVar(Element elem) {

        var code = new StringBuilder();

        if (elem instanceof ArrayOperand) {code.append(loadArray((ArrayOperand) elem) + NL);}
        else if (elem instanceof Operand) {code.append(loadOperand((Operand) elem) + NL);}
        else if (elem instanceof LiteralElement){ code.append(loadLiteralVal((LiteralElement) elem) + NL);}

        return code.toString();

    }


    private String loadLiteralVal(LiteralElement elem) {

        var code = new StringBuilder();

        pushS(1);

        String literal = elem.getLiteral();
        ElementType elementType = elem.getType().getTypeOfElement();


        //var varTable = getvarTable();

        if (!(elementType == ElementType.INT32 || elementType == ElementType.BOOLEAN)) {

        } else {
            int val = Integer.parseInt(literal);

            if (isBetween(val, -1, 5)) code.append("iconst_");
            else if (isBetween(val, -128, 127)) code.append("bipush ");
            else if (isBetween(val, -32768, 32767)) code.append("sipush ");
            else {code.append(TAB); code.append("ldc ");};

            if (val == -1) {
                code.append("m1");
            } else {
                code.append(val);
            }

        }
        return code.toString();
    }



    private String loadArray(ArrayOperand elem) {

        this.pushS(1);

        var code = new StringBuilder();

        //var table = this.currentMethod.getVarTable();

        var elemToLoad = elem.getIndexOperands().get(0);

        var index = getVarIndex(elem.getName());

        code.append(TAB + "aload" + index)
                .append(NL)
                .append(loadVar(elemToLoad))

                .append("iaload");


        popS(1);


        return code.toString();

    }

    private String loadOperand(Operand oper) {

        var code = new StringBuilder();

        this.pushS(1);

        if (oper.getType().getTypeOfElement().equals(THIS)){
            code.append("aload_0");
        } else if (oper.getType().getTypeOfElement().equals(STRING) || oper.getType().getTypeOfElement().equals(ARRAYREF) || oper.getType().getTypeOfElement().equals(OBJECTREF)) {
            code.append(TAB).append("aload").append(getVarIndex(oper.getName()));
        } else if (oper.getType().getTypeOfElement().equals(BOOLEAN) || oper.getType().getTypeOfElement().equals(INT32)) {
            code.append("iload").append(getVarIndex(oper.getName()));
        }

        //varIndex para objeto


        return code.toString();

    }


    private String getVarIndex(String var_name) {

        var code = new StringBuilder();



        var table = currentMethod.getVarTable();




        if (var_name.equals("this")) {
            code.append("_0");
        } else {
            var num = table.get(var_name).getVirtualReg();

            if (num < 4){
                code.append("_");
            }
            else code.append(" ");
            code.append(num);
        }

        return code.toString();
    }







    //apagar tudo a partir daqui
    private String getClassNameFromImports(String class_name) {

        var code = new StringBuilder();


        if (class_name.equals("this")) {
            code.append(classUnit.getClassName());
            return code.toString();
        }


        var imports = this.classUnit.getImports();

        for (var className : imports) {

            if (className.endsWith(class_name)) {
                code.append(nameOfClassNorm(className));
                return code.toString();



            }
        }


        return class_name;
    }


    private String nameOfClassNorm(String nameOfClass) {

        var code = new StringBuilder();

        nameOfClass.replaceAll("\\.", "/");

        code.append(nameOfClass);

        return code.toString();


    }



    private String getInst(Instruction inst) {

        var Tipo = inst.getInstType();

        var varTable = getvarTable();

        switch (Tipo) {
            case CALL -> {return this.generateCall((CallInstruction) inst);}
            case GOTO -> {return this.generateGotoInstruction((GotoInstruction) inst);}
            case BRANCH -> {return this.generateCondBranchInstruction((CondBranchInstruction) inst);}
            case PUTFIELD -> {return this.generatePutField((PutFieldInstruction) inst);}
            case GETFIELD -> {return this.generateGetField((GetFieldInstruction) inst);}
            case ASSIGN -> {return this.generateAssign((AssignInstruction) inst);}
            case UNARYOPER -> {return this.generateUnaryOpInstruction((UnaryOpInstruction) inst);}
            case BINARYOPER -> {return this.generateBinaryOp((BinaryOpInstruction) inst);}
            case NOPER -> {return this.generateNoper((SingleOpInstruction) inst);}
            case RETURN -> {return this.generateReturn((ReturnInstruction) inst);}
        };

        return "";


    }





    private String[] generateBinaryBranch(BinaryOpInstruction BinatyOpInst) {


        String array[] = new String[5];


        var varTable = getvarTable();

        Integer literalVal = null;

        var typeOp = BinatyOpInst.getOperation().getOpType();

        var code = new StringBuilder();

        switch (typeOp) {

            //less th <
            case LTH:

                var left = BinatyOpInst.getLeftOperand();

                var right = BinatyOpInst.getRightOperand();


                // (num var)
                if (left instanceof LiteralElement) {
                    literalVal = Integer.parseInt(((LiteralElement) left).getLiteral());
                    array[1] = loadVar(BinatyOpInst.getRightOperand());
                    array[0] = "ifgt";
                }



                // (var num))
                if (right instanceof LiteralElement) {
                    literalVal = Integer.parseInt(((LiteralElement) right).getLiteral());
                    array[1] = loadVar(BinatyOpInst.getLeftOperand());
                    array[0] = "iflt";
                }

                // ((var var)

                if (literalVal == null || literalVal != 0){

                    var l = loadVar(left); var r = loadVar(right);
                    array[1] = l + r;
                    array[0] = "if_icmplt";
                }
                break;

            case ANDB:
                array[0] = "ifne";
                array[1] = getInst(BinatyOpInst);
                break;

                ///gte >=
            case GTE:

                var left_ = BinatyOpInst.getLeftOperand();

                var right_ = BinatyOpInst.getRightOperand();


                // (num var)

                if (left_ instanceof LiteralElement) {
                    literalVal = Integer.parseInt(((LiteralElement) left_).getLiteral());
                    array[0] = "ifge";
                    array[1] = loadVar(right_);
                }

                // (var num)
                if (right_ instanceof LiteralElement) {
                    literalVal = Integer.parseInt(((LiteralElement) right_).getLiteral());
                    array[0] = "ifle";
                    array[1] = loadVar(left_);
                }


                // (var var)
                if (literalVal == null || literalVal != 0){
                    array[0] = "if_icmpge";
                    var l = loadVar(left_); var r = loadVar(right_);
                    array[1] = l + r;
                }
                break;


            default:
                return new String[]{""};
        };


        return array;
    }




    private String generateCondBranchInstruction(CondBranchInstruction CondBranchInst){

        String result[];
        String operToPerform = "";
        var code = new StringBuilder();
        Instruction cond = null;


        var varTable = getvarTable();


        if (CondBranchInst instanceof OpCondInstruction) {
            OpCondInstruction helperOp = (OpCondInstruction) CondBranchInst;
            cond = helperOp.getCondition();
        }


        if (CondBranchInst instanceof SingleOpCondInstruction) {
            SingleOpCondInstruction helperSingle = (SingleOpCondInstruction) CondBranchInst;
            cond = helperSingle.getCondition();
        }


        var condType = cond.getInstType();

        switch (condType) {
            case BINARYOPER -> {
                result = generateBinaryBranch((BinaryOpInstruction) cond);
                operToPerform = result[0];
                code.append(result[1]);
            }


            case UNARYOPER -> {
                var helper = (UnaryOpInstruction) cond;
                var opType = helper.getOperation().getOpType();
                if (opType.equals(OperationType.NOTB)) {
                    operToPerform = "ifeq";
                    code.append(loadVar(helper.getOperand()));
                }


            }

            default -> {
                code.append(getInst(cond));
                operToPerform = "ifne";

            }


        }


        code.append(TAB).append(operToPerform)
                .append(" ").append(CondBranchInst.getLabel()).append(NL);




        if (operToPerform.equals("if_icmge")
                || operToPerform.equals("if_icmplt")) {
            popS(2);
        } else popS(1);



        return code.toString();
    }

    private String generateSingleOpCondInstruction(SingleOpCondInstruction singleOpInst) {


        String operToDo = null;
        String array[];
        var code = new StringBuilder();

        var varTable = getvarTable();

        Instruction cond = singleOpInst.getCondition();


        var tipoCond = cond.getInstType();

        switch (tipoCond) {
        case UNARYOPER -> {
            var helper = (UnaryOpInstruction) cond;
            if (helper.getOperation().getOpType().equals(OperationType.NOTB)) {
                operToDo = "ifeq";
                code.append(loadVar(helper.getOperand()));
            }


        }

        case BINARYOPER -> {
            array = generateBinaryBranch((BinaryOpInstruction) cond);
            operToDo = array[0];
            code.append(array[1]);

        }
        default -> {
            code.append(getInst(cond));
            operToDo = "ifne";
        }


        }


        code.append(TAB).append(operToDo)
                .append(" ").append(singleOpInst.getLabel())
                .append(NL);




        return code.toString();
    }


    private String getOper(Operation oper){
        var code = new StringBuilder();
        var tipo = switch (oper.getOpType()) {
            case ANDB -> "iand";
            case NOTB -> "ifeq";
            case GTE -> "if_icmpte";
            case LTH -> "if_icmplt";
            case MUL -> "imul";
            case DIV -> "idiv";
            case ADD -> "iadd";
            case SUB -> "isub";
            default -> throw new NotImplementedException(oper.getOpType());
        };

        code.append(tipo);
        return code.toString();
    }



    private String generateUnaryOpInstruction(UnaryOpInstruction UnaryInst){
        var code = new StringBuilder();

        var varTable = getvarTable();

        var opType = UnaryInst.getOperation().getOpType();
        code.append(loadVar(UnaryInst.getOperand())).append(TAB)

                .append(getOper(UnaryInst.getOperation()));
        if (opType.equals(OperationType.NOTB)) {
            code.append(getJumpsBool());
        }
        code.append(NL);

        return code.toString();
    }


    private String getJumpsBool() {

        var code = new StringBuilder();

        var JumpNum = this.IndexJUMP;

        code.append(" ").append("true").append(JumpNum)
                .append(NL).append(TAB)
                .append("iconst_0")
                .append(NL).append(TAB)
                .append("goto jump")
                .append(JumpNum).append(NL)
                .append("true").append(JumpNum)
                .append(":").append(NL).append(TAB)
                .append("iconst_1").append(NL)
                .append("jump").append(JumpNum).append(":").append(NL);


        this.IndexJUMP++;

        return code.toString();
    }



    private String generateGotoInstruction(GotoInstruction gotoInst) {
        var code = new StringBuilder();
        var varTable = getvarTable();

        code.append("goto").append(" ").append(gotoInst.getLabel()).append(NL);
        return code.toString();
    }



    private String generateNoper(SingleOpInstruction instr){
        var code = new StringBuilder();

        code.append(loadVar(instr.getSingleOperand())).append(NL);

        return code.toString();
    }



    private String generateArrayAssignment(AssignInstruction assignInst) {

        var reg = this.currentMethod.getVarTable();

        this.pushS(1);

        var code = new StringBuilder();


        Operand oper = (Operand) assignInst.getDest();

        String index = getVarIndex(oper.getName());

        code.append("aload").append(index).append(NL)
                .append(loadVar(((ArrayOperand) oper).getIndexOperands().get(0)));



        code.append(getInst(assignInst.getRhs()));
            code.append(storeVar(oper)).append(NL);


        return code.toString();
    }



    private String generateArrayCall(CallInstruction callInst) {


        //to update the args to pop
        this.argsToPop = -1;


        var code = new StringBuilder();

        for (Element elemArr : callInst.getArguments()) {
            this.argsToPop++;
            code.append(loadVar(elemArr));

        }


        code.append(TAB).append("newarray int")
                .append(NL);

        return code.toString();
    }



    private String generateLengthCall(CallInstruction callInst) {
        var code = new StringBuilder();

        var oper = callInst.getOperands().get(0);
        code.append(loadVar(oper)).append(TAB)
                .append("arraylength").append(NL);

        return code.toString();
    }

    private String generateLdcCall(CallInstruction CallInst) {
        var code = new StringBuilder();

        var oper = CallInst.getOperands().get(0);

        code.append(loadVar(oper));

        return code.toString();
    }



    private String generateReturn(ReturnInstruction RetInst) {

        var code = new StringBuilder();


        var elem = RetInst.getOperand();

        if (RetInst.hasReturnValue()) {
            code.append(loadVar(elem));
            code.append(TAB);
        } else {
            code.append(TAB);
        }



        if (!(elem == null)) {
            if (elem.getType().getTypeOfElement().equals(ElementType.BOOLEAN) ||
                    elem.getType().getTypeOfElement().equals(ElementType.INT32)) {
                code.append("i");
            } else {
                code.append("a");
            }

        }

        code.append("return")
                .append(NL);

        return code.toString();
    }





    private int getLimitFromLocals(Method method_) {


        Set<Integer> virt_Regs = new HashSet<>();
        virt_Regs.add(0); //the default one
        var code = new StringBuilder();


        var varTableVals = method_.getVarTable().values();

        for (var var : varTableVals) {
            virt_Regs.add(var.getVirtualReg());
        }


        Integer size = virt_Regs.size();
        return size;
    }



    private Boolean popS(int quant) {
        this.currentStackSiz -= quant;
        return true;
    }

    private Boolean pushS(int quant) {
        this.currentStackSiz += quant;
        this.stackLimit = retMax(this.currentStackSiz, this.stackLimit);
        return true;
    }


    private int retMax(int a, int b){
        return Math.max(a,b);
    }

















}
