package simplespy.compiler2017.NodeFamily;

import simplespy.compiler2017.FrontEnd.ASTVisitor;

/**
 * Created by spy on 17/3/27.
 */
public class IDNode extends ExprNode {
    public final String name;
    public final Location loc;
    public Node entity;

    public Location getLoc() {
        return loc;
    }
    public IDNode(String name, Location loc) {
        this.name = name;
        this.loc = loc;
        isLv = true;
    }

    public boolean isLv;

    public boolean Lv(){
        return this.isLv;
    }

    public String getName() {
        return name;
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
    public void setEntity(Node ent) {
        if (entity != null) return;
        this.entity = ent;
    }

    @Override
    public TypeNode getType() {
        if (name.equals( "size"))
            return new BaseType(TypeNode.TYPENAME.INT, getLoc());
        if (entity == null){
            return null;
        }
        return entity.getType();
    }

    public Node getEntity() {
        if (entity instanceof VarDecInBlockNode) return ((VarDecInBlockNode) entity).getVardec();
        return entity;
    }
    public boolean isLoadable() {
        if (this.getEntity() instanceof FuncDefNode) return false;
        if (this.type instanceof ArrayType) return false;
        return true;
    }


}
