package simplespy.compiler2017.NodeFamily;

import simplespy.compiler2017.FrontEnd.ASTVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by spy on 17/3/25.
 */
public class ClassDefNode extends ASTBranch {
    public final String name;
    public final List<ASTBranch> members;//Var or Func
    public final Location loc;

    public Location getLoc() {
        return loc;
    }



    public ClassDefNode(String name, Location loc){
        this.name = name;
        this.loc = loc;
        members = new ArrayList<>();

    }


    public void add(Object member){
        if(member instanceof ASTBranch) members.add((ASTBranch) member);
        else if (member instanceof List) ((List) member).stream().forEachOrdered(this::add);
    }

    public String getName() {
        return name;
    }

    public List<ASTBranch> getMembers() {
        return members;
    }

    @Override
    public void accept(ASTVisitor visitor) {
        visitor.visit(this);
    }
}
