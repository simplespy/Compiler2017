package simplespy.compiler2017.NodeFamily;

import simplespy.compiler2017.FrontEnd.ASTVisitor;

/**
 * Created by spy on 17/3/25.
 */
public class WhileNode extends StmtNode {
    public ExprNode condition;
    public StmtNode body;
    public final Location loc;

    public Location getLoc() {
        return loc;
    }
    public WhileNode(ExprNode condition, StmtNode body, Location loc){
        this.condition = condition;
        this.body = body;
        this.loc = loc;
    }
    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }


}
