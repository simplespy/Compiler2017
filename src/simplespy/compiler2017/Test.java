package simplespy.compiler2017;

import simplespy.compiler2017.Asm.AssemblyCode;
import simplespy.compiler2017.BackEnd.*;
import simplespy.compiler2017.BackEnd.SIR.SIR;
import simplespy.compiler2017.Exception.CompilationError;
import simplespy.compiler2017.FrontEnd.*;
import simplespy.compiler2017.NodeFamily.ASTRoot;
import simplespy.compiler2017.NodeFamily.IRNode.IRRoot;
import simplespy.compiler2017.Parser.SimpilerLexer;
import simplespy.compiler2017.Parser.SimpilerParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;

/**
 * Created by spy on 17/3/25.
 */
public class Test {
    public static void main(String[] args) throws Exception {
        boolean pass = true;
        String sdir = "Test/All/";
        File dir = new File(sdir);
        String[] children = dir.list();
        if (children == null) {
            System.out.println("No directory");
        } else {
            for (int i = 0; i < children.length; ++i) {
                String filename = children[i];


                if (!filename.contains(".txt")) continue;
                System.out.println(filename);
                InputStream is = new FileInputStream(sdir + filename); // or System.in;
                CompilationError.initialize();

                ANTLRInputStream input = new ANTLRInputStream(is);
                SimpilerLexer lexer = new SimpilerLexer(input);
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                SimpilerParser parser = new SimpilerParser(tokens);
                parser.setErrorHandler(new BailErrorStrategy());

                ParseTree tree = parser.program();

                ParseTreeWalker walker = new ParseTreeWalker();
                ASTBuilder builder = new ASTBuilder();
                walker.walk(builder, tree);
                ASTRoot ast = builder.getAst();
                PrintStream os = new PrintStream("Output/"+filename.substring(0, filename.length()-3)+"out");

                ASTPrinter printer = new ASTPrinter(os);
                ast.accept(printer);

                ScopeBuilder scopeBuilder = new ScopeBuilder();
                ast.accept(scopeBuilder);

                TypeResolver typeResolver = new TypeResolver();
                ast.accept(typeResolver);

                DereferenceChecker DChecker = new DereferenceChecker();
                ast.accept(DChecker);

                CompilationError.printExceptions();

              //  SpecialChecker specialChecker = new SpecialChecker();
            //    ast.accept(specialChecker);

                IRGenerator irGenerator = new IRGenerator();
                ast.accept(irGenerator);
                IRRoot ir = irGenerator.getIR();


                IRPrinter irPrinter = new IRPrinter(os);
                ir.accept(irPrinter);

                IRTransformer irTransformer = new IRTransformer();
                ir.accept(irTransformer);
                SIR sir = irTransformer.getSir();

                SimpleAllocator simpleAllocator = new SimpleAllocator(sir);
                simpleAllocator.run();

                CodeBuilder codeBuilder = new CodeBuilder(sir);
                codeBuilder.build();
                AssemblyCode ac = codeBuilder.getAC();
                ASMPrinter asmPrinter = new ASMPrinter(os);
                ac.accept(asmPrinter);
                BufferedReader br = new BufferedReader(new FileReader("lib/builtin_functions.asm"));
                String line;
                while ((line = br.readLine()) != null) os.println(line);

             //   ASMPrinter asmPrinter = new ASMPrinter(os);
              //  ac.accept(asmPrinter);

                if (!CompilationError.exceptions.isEmpty()) {
                    CompilationError.printExceptions();
                    throw new Exception();
                }


            }
        }
    }
}

