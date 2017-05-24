package simplespy.compiler2017.BackEnd;

import simplespy.compiler2017.Asm.*;
import simplespy.compiler2017.FrontEnd.IRVisitor;
import simplespy.compiler2017.FrontEnd.Scope;
import simplespy.compiler2017.NodeFamily.*;
import simplespy.compiler2017.NodeFamily.IRNode.*;
import simplespy.compiler2017.Utils.AsmUtils;
import simplespy.compiler2017.Utils.ListUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Created by spy on 5/18/17.
 */
public class CodeGenerator implements IRVisitor {
    public static AssemblyCode ac;
    private Map<String, StringLiteralNode> stringPool;
    String currentLabel;
    int numofGlobalString;

    public static AssemblyCode getAC() {
        return ac;
    }

    @Override
    public void visit(IRRoot node) {
        stringPool = node.typeTable.getStringMap();
        locateSymbols(node);
        ac = new AssemblyCode();
        if (node.vars != null){
            numofGlobalString = 0;
            generateDataSection(ac, node.vars);
        }
        if (!stringPool.isEmpty() && numofGlobalString != stringPool.keySet().size()){
            generateReadOnlyDataSection(ac);
        }
        generateTextSection(ac, node.funcs);
    }

    private void locateSymbols(IRRoot ir) {
        for (VarDecNode var : ir.vars){
            Symbol sym = new Symbol(var.getName());
            var.setAddress(sym);
            var.setMemoryReference(new DirectMemoryReference(sym));
        }


    }


    private void generateDataSection(AssemblyCode file, List<VarDecNode> gvars){
        file._data();
        file.label("fmtd");
        file.define(new ImmediateValue(0),new Symbol("\"%d\""));
        file.label("fmts");
        file.define(new ImmediateValue(0),new Symbol("\"%s\""));
        gvars.stream().filter(x->x.init != null).forEachOrdered(x ->{
            Symbol sym = file.label(x.getName());
            x.setAddress(sym );
            x.setMemoryReference(new DirectMemoryReference(sym));
            initialize(x);
        });
        file._bss();
        gvars.stream().filter(x->x.init == null).forEachOrdered(x ->{
        });
        gvars.stream().forEachOrdered(this::visit);
    }

    private void initialize(VarDecNode node){
        if (node.ir instanceof Int){
            ac.define(new ImmediateValue(((Int) node.ir).getValue()));
        }else if (node.ir instanceof Str){
            ac.define(new Symbol(((Str) node.ir).getValue()));
            stringPool.get(((Str) node.ir).getValue()).isGlobal = true;
            numofGlobalString++;
        }else {
            throw new Error("unknown Initializer");
        }
    }

    private void generateReadOnlyDataSection(AssemblyCode file){
        file._rodata();
        stringPool.keySet().stream().filter(name -> stringPool.get(name).isGlobal == false).forEachOrdered(name -> {
            Label label = new Label();
            file.label(label);
            Symbol symbol = new Symbol(name);
            stringPool.get(name).setAddress(label.getSymbol());
            stringPool.get(name).setMemoryReference(new DirectMemoryReference(label.getSymbol()));
            file.define(new ImmediateValue(0),symbol);
        });
    }
    private void generateTextSection(AssemblyCode file, Map<String, FuncDefNode> funcs){
        file._text();
        funcs.keySet().stream().forEachOrdered(x->{
            file.label(x);
            compileFunctionBody(file, funcs.get(x));
        });

    }

    private void compileFunctionBody(AssemblyCode file, FuncDefNode func){
        StackFrame frame = new StackFrame();
        locateParameters(func.parameters);
        frame.lvarSize = locateLocalVars(func.scope);
        AssemblyCode body = compileStmts(func);
        frame.saveRegs = usedCalleeSaveRegisters(body);
        frame.tempSize = ac.virtualStack.getMax();

        fixLocalVariableOffsets(func.scope, frame.lvarOffset());
        fixTempVariableOffsets(body, frame.tempOffset());

        generateFunctionBody(file, body, frame);

    }
    static final private int PARAM_START_WORD = 2;
    static final public int STACK_WORD_SIZE = 8;



    private void locateParameters(List<VarDecNode> paras){
        int numWords = PARAM_START_WORD;
        for(VarDecNode var : paras){
            var.setMemoryReference(new IndirectMemoryReference(numWords * STACK_WORD_SIZE, bp()));
            ++numWords;
        }
    }

    private AssemblyCode acfunc;

    private AssemblyCode compileStmts(FuncDefNode func){
        acfunc = new AssemblyCode();
        func.ir.stream().forEachOrdered(this::visit);
        return acfunc;
    }

    private List<Register> calleeSaveRegistersCache = null;

    private List<Register> calleeSaveRegisters() {
        if (calleeSaveRegistersCache == null) {
            List<Register> regs = new ArrayList<>();
            for (Register.RegisterClass c : CALLEE_SAVE_REGISTERS) {
                regs.add(new Register(c));
            }
            calleeSaveRegistersCache = regs;
        }
        return calleeSaveRegistersCache;
    }
    private List<Register> usedCalleeSaveRegisters(AssemblyCode body) {
        List<Register> result = calleeSaveRegisters().stream().filter(reg -> body.doesUses(reg)).collect(Collectors.toList());
        result.remove(bp());
        return result;
    }



    private void fixLocalVariableOffsets(Scope scope, int len){
        for (Node var : scope.getEntities().values()){
            if (var instanceof VarDecInBlockNode){
                ((VarDecInBlockNode) var).getVardec().getMemoryReference().fixOffset(-len);
            }
            else var.getMemoryReference().fixOffset(-len);
        }
    }

    private void fixTempVariableOffsets(AssemblyCode asm, int len){
        asm.virtualStack.fixOffset(-len);
    }

    private void generateFunctionBody(AssemblyCode file, AssemblyCode body, StackFrame frame){
        file.virtualStack.reset();
        prologue(file, frame.saveRegs, frame.frameSize());
        file.addAll(body.getAssemblies());
        file.addExtern(body.getExterns());
        epilogue(file, frame.saveRegs);
        file.virtualStack.fixOffset(0);

    }


    private void prologue(AssemblyCode file, List<Register> saveRegs, int frameSize){
        file.push(bp());
        file.mov(sp(), bp());
        saveRegs.stream().forEachOrdered(x->file.virtualPush(x));
        if (frameSize > 0) file.sub(new ImmediateValue(frameSize), sp());
    }

    private void epilogue(AssemblyCode file, List<Register> saveRegs){
        ListUtils.reverse(saveRegs).stream().forEachOrdered(x->file.virtualPop(x));
        file.mov(bp(),sp());
        file.pop(bp());
        file.ret();
    }




    private void compileExpr(Expr node, Register reg){
        if(node instanceof Var){
            acfunc.mov(((Var)node).memref(), reg);
        }
        else if (node instanceof  Int){
            acfunc.mov(new ImmediateValue(((Int)node).getValue()), reg);
        }
        else if (node instanceof Str){

            acfunc.mov(stringPool.get(((Str)node).getValue()).getMemoryReference(), reg);
        }
    }


    @Override
    public void visit(ExprStmt node) {
        visit(node.getExpr());
    }

    @Override
    public void visit(Bin node) {
        visit(node.getRight());
        acfunc.virtualPush(ax());
        visit(node.getLeft());
        acfunc.virtualPop(cx());
        compileBinaryOp(node.getOp(), ax(), cx());
    }

    private void compileBinaryOp(BinaryOpNode.BinaryOp op, Register left, Operand right){
        switch (op) {
            case ADD:
                acfunc.add(right, left);
                break;
            case SUB:
                acfunc.sub(right, left);
                break;
            case MUL:
                acfunc.mul(right, left);
                break;
            case DIV:
            case MOD:
                acfunc.mov(new ImmediateValue(0), dx());
                acfunc.div(cx());
                if (op == BinaryOpNode.BinaryOp.MOD) {
                    acfunc.mov(dx(), left);
                }
                break;
            case BITWISE_AND:
                acfunc.and(right, left);
                break;
            case BITWISE_OR:
                acfunc.or(right, left);
                break;
            case XOR:
                acfunc.xor(right, left);
                break;
            case SHL:
                acfunc.sal(cl(), left);
                break;
            case SHR:
                acfunc.shr(cl(), left);
                break;
            default:
                // Comparison operators
                acfunc.cmp(right, ax());
                switch (op) {
                    case EQ:      acfunc.je(thenLabel); break;
                    case NE:      acfunc.jne(thenLabel); break;
                    case GT:      acfunc.jg(thenLabel); break;
                    case GE:    acfunc.jge(thenLabel); break;
                    case LT:      acfunc.jl(thenLabel); break;
                    case LE:    acfunc.jle(thenLabel); break;
                    default:
                        throw new Error("unknown binary operator: " + op);
                }
                acfunc.jmp(elseLabel);
        }
    }

    static int varnum = 0;
    static String varBase = "var";

    @Override
    public void visit(Call node) {
        String funcName = node.getName().getEntityForce().getName();

        Register[] paras = {di(), si(), dx(), cx(), r8(), r9()};

        if (funcName.equals("getInt")){
            Symbol var = new Symbol(varBase + varnum++);
            ac.addBss(var.name+':' + "resd 1" );
            acfunc.mov(var, si());
            acfunc.mov(new Symbol("fmtd"), di());
            acfunc.call(new Symbol(transFuncName(funcName)));
            acfunc.mov(var,ax());
        }

        else if (funcName.equals("getString")){
            Symbol var = new Symbol(varBase + varnum++);
            ac.addBss(var.name+':' + "resd 20" );
            acfunc.mov(var, si());
            acfunc.mov(new Symbol("fmts"), di());
            acfunc.call(new Symbol(transFuncName(funcName)));
            acfunc.mov(var,ax());

        }

        else if (funcName.equals("toString")){
            Symbol var = new Symbol(varBase + varnum++);
            ac.addBss(var.name+':' + "resd 20" );
            if (node.getArgs().size() == 1){
                Expr arg = node.getArgs().get(0);
                if (arg instanceof Int){
                    acfunc.mov(new ImmediateValue(((Int) arg).getValue()),dx());
                    acfunc.mov(new Symbol("fmtd"), si());
                    acfunc.mov(var, di());
                    acfunc.call(new Symbol(transFuncName(funcName)));
                    acfunc.mov(var,ax());
                }
            }else throw new Error("Function toString must have int parameter");


        }

        else{
            int i = 0;
            for (Expr arg: ListUtils.reverse(node.getArgs())){
                compileExpr(arg, paras[node.getArgs().size()-1-i]);
                if(i >= paras.length) throw new Error("more than 6 paras");
                ++i;
            }
            acfunc.call(new Symbol(transFuncName(funcName)));

        }




    }

    private String transFuncName(String name){
        String externName;
        if (name.equals("println")) externName = "puts";
        else if (name.equals("print")) externName = "printf";
        else if (name.equals("getString") || name.equals("getInt")) externName = "scanf";
        else if (name.equals("toString")) externName = "sprintf";
        else return name;
        acfunc.addExtern(new Symbol(externName));
        return externName;
    }

    private Label thenLabel;
    private Label elseLabel;

    @Override
    public void visit(CJump node) {
        thenLabel = node.getThen();
        elseLabel = node.getOtherwise();
        visit(node.getCond());
    }



    @Override
    public void visit(Expr node) {
        node.accept(this);
    }

    @Override
    public void visit(Int node) {
        acfunc.mov(new ImmediateValue(node.getValue()), ax());
    }

    @Override
    public void visit(Jump node) {
        acfunc.jmp(node.getLabel());
    }

    @Override
    public void visit(LabelStmt node) {acfunc.label(node.label);}










    @Override
    public void visit(Return node) {
        if (node.getExpr() != null){
            node.getExpr().accept(this);
        }
        //acfunc.jmp(epilogue);
    }

    @Override
    public void visit(Str node) {
        StringLiteralNode ent = stringPool.get(node.getValue());
        acfunc.mov(ent.getAddress(), ax());
    }

    @Override
    public void visit(Uni node) {
        visit(node.getExpr());
        switch (node.getOp()){
            case NEG:
                acfunc.neg(ax());
                break;
            case LOGICAL_NOT:
                acfunc.test(ax(),ax());
              //  acfunc.sete(al);
                acfunc.movzx(al(),ax());
            case BITWISE_NOT:
                acfunc.not(ax());
                break;
            default:
        }
    }

    @Override
    public void visit(Var node) {
        acfunc.mov(node.memref(), ax());

    }

    @Override
    public void visit(VarDecNode node) {}

    @Override
    public void visit(Assign node) {
        if(node.getLhs().isAddr() && ((Addr)node.getLhs()).getMemoryReference() != null){
            visit(node.getRhs());
            acfunc.mov(ax(), ((Addr) node.getLhs()).getMemoryReference());
        }
        else if (node.getRhs().isConstant()){
            visit(node.getLhs());
            acfunc.mov(ax(),cx());
            acfunc.mov(ax(), new IndirectMemoryReference(0,cx()));
        }
        else{
            visit(node.getRhs());
            acfunc.virtualPush(ax());
            visit(node.getLhs());
            acfunc.mov(ax(),cx());
            acfunc.virtualPop(ax());
            acfunc.mov(ax(), new IndirectMemoryReference(0,cx()));
        }


    }

    @Override
    public void visit(FuncDefNode node) {

    }

    @Override
    public void visit(Stmt node) {
        node.accept(this);

    }

    @Override
    public void visit(Addr node) {
        if (node.getAddress() != null){
            acfunc.mov(node.getAddress(), ax());
        }else {
            acfunc.lea(node.getMemoryReference(),ax());
        }
    }




    private int locateLocalVars(Scope scope){
        return locateLocalVars(scope, 0);
    }
    private int locateLocalVars(Scope scope, int parentStackLen){
        int len = parentStackLen;
        for (Node vari : scope.getEntities().values()) {
            VarDecNode var;
            if (vari instanceof VarDecInBlockNode) {
                var = ((VarDecInBlockNode) vari).getVardec();
            }else if (vari instanceof VarDecNode){
                var = (VarDecNode) vari;
            }else break;
            len = AsmUtils.align(len + var.getType().size(), 16);
            var.setMemoryReference(new IndirectMemoryReference(-len, bp()));

        }
        int maxLen = len;
        for (Scope s : scope.getChildren()){
            int childLen = locateLocalVars(s, len);
            maxLen = Math.max(maxLen, childLen);
        }
        return maxLen;
    }
    private Register ax(){
        return new Register(Register.RegisterClass.AX);
    }
    private Register cx(){return new Register(Register.RegisterClass.CX);}
    private Register dx(){return new Register(Register.RegisterClass.DX);}
    private Register al(){return new Register(Register.RegisterClass.AL);}
    private Register bp(){return new Register(Register.RegisterClass.BP);}
    private Register sp(){
        return new Register(Register.RegisterClass.SP);
    }
    private Register cl(){
        return new Register(Register.RegisterClass.CL);
    }
    private Register di(){
        return new Register(Register.RegisterClass.DI);
    }
    private Register si(){
        return new Register(Register.RegisterClass.SI);
    }
    private Register r8(){return new Register(Register.RegisterClass.R8);}
    private Register r9(){return new Register(Register.RegisterClass.R9);}

    static final Register.RegisterClass[] CALLEE_SAVE_REGISTERS = {
            Register.RegisterClass.BX, Register.RegisterClass.BP,
            Register.RegisterClass.SI, Register.RegisterClass.DI
    };


    class StackFrame{
        public List<Register> saveRegs;
        public int lvarSize;
        public int tempSize;

        public StackFrame(){

        }
        public int saveRegsSize(){
            return saveRegs.size() * STACK_WORD_SIZE;
        }
        public int lvarOffset(){return saveRegsSize();}
        public int tempOffset(){return saveRegsSize() + lvarSize;}
        public int frameSize(){return saveRegsSize() + lvarSize + tempSize;}

    }
}
