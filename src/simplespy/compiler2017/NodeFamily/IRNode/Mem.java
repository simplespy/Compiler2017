package simplespy.compiler2017.NodeFamily.IRNode;

import simplespy.compiler2017.FrontEnd.IRVisitor;

/**
 * Created by spy on 5/17/17.
 */
public class Mem extends Expr {
    protected Expr expr;
    public Mem(Expr expr){
        this.expr = expr;
    }
    public Expr addressNode() {
        return expr;
    }
    @Override
    public void accept(IRVisitor visitor) {
        visitor.visit(this);
    }
}
