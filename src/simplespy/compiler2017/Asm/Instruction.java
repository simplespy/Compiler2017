package simplespy.compiler2017.Asm;


import simplespy.compiler2017.BackEnd.ASMVisitor;
import simplespy.compiler2017.Utils.ListUtils;

import java.util.*;

/**
 * Created by spy on 5/18/17.
 */
public class Instruction extends Assembly {
    public String op;
    public Operand[] operands;
    protected boolean needRelocation;
    public List<Instruction> next = new LinkedList<>();
    public List<Register> def = new ArrayList<>();
    public List<Register> use = new ArrayList<>();
    public Set<Register> in = new HashSet<>();
    public Set<Register> out = new HashSet<>();

    public void setDefUse(){

    }


    public Instruction(String op, Operand src, Operand dest){
        this.op = op;
        this.operands = new Operand[] {src, dest};
    }
    public Instruction(String op, Operand ope){
        this.op = op;
        this.operands = new Operand[] {ope};
    }
    public Instruction(String op){
        this.op = op;
    }


    public void collectStatistics(Statistics stats) {
        stats.instructionUsed(op);
        if (operands == null) return;
       try{
           for (int i = 0; i < operands.length; i++) {
            operands[i].collectStatistics(stats);
        }
       }catch(Exception e) {
            throw new Error("instruction error");
        }
    }
    public Symbol jmpDestination() {
        if (operands[0] instanceof Symbol)return (Symbol) operands[0];
        DirectMemoryReference ref = (DirectMemoryReference)operands[0];
        return ref.value;
    }
    @Override
    public String toString() {
        String line = op;
        if (operands == null) return line;
        for (int i = operands.length-1; i >= 0; --i){
            if (i == operands.length-1) line += " ";
            else line += ", ";
            if (operands[i] == null){
                throw new Error("operand");
            }
            line += operands[i].toString();
        }
    if (comment != null) line += comment;
        return line;
    }

    @Override
    public void accept(ASMVisitor visitor) {
        visitor.visit(this);
    }


}
