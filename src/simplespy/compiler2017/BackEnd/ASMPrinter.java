package simplespy.compiler2017.BackEnd;

import simplespy.compiler2017.Asm.*;
import simplespy.compiler2017.BackEnd.SIR.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintStream;

/**
 * Created by spy on 5/18/17.
 */
public class ASMPrinter implements ASMVisitor {
    PrintStream out;

    public ASMPrinter(PrintStream out){
        this.out = out;
    }

    @Override
    public void visit(AssemblyCode asm) {
        out.println(";---------------------------- ");
        out.println(";Aha!I'm x86 Code!");
        out.println(";---------------------------- ");
        out.println("global main");
        asm.getExterns().stream().forEachOrdered(this::visit);
        asm.getBss().stream().forEachOrdered(x->out.println(x));
        asm.getAssemblies().stream().forEachOrdered(this::visit);

    }

    @Override
    public void visit(Assembly asm) {
        asm.accept(this);
    }



    @Override
    public void visit(Directive asm) {
        out.println(asm.toString());

    }

    @Override
    public void visit(Instruction asm) {
        out.println('\t'+asm.toString());
    }

    @Override
    public void visit(Label asm) {
        out.println(asm.toString()+":");
    }

    @Override
    public void visit(Align asm) {
        out.println('\t'+asm.toString());
    }

    @Override
    public void visit(Binary ins) {

    }

    @Override
    public void visit(Branch ins) {

    }

    @Override
    public void visit(CallFunc ins) {

    }

    @Override
    public void visit(Jmp ins) {

    }

    @Override
    public void visit(Labelline ins) {

    }

    @Override
    public void visit(Move ins) {

    }

    @Override
    public void visit(Unary ins) {

    }
}
