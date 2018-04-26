package mips;

import ast.*;
import types.*;

public class CodeGen implements ast.CommandVisitor {
    
    private StringBuffer errorBuffer = new StringBuffer();
    private TypeChecker tc;
    private Program program;
    private ActivationRecord currentFunction;

    public CodeGen(TypeChecker tc)
    {
        this.tc = tc;
        this.program = new Program();
    }
    
    public boolean hasError()
    {
        return errorBuffer.length() != 0;
    }
    
    public String errorReport()
    {
        return errorBuffer.toString();
    }

    private class CodeGenException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        CodeGenException(String errorMessage) {
            super(errorMessage);
        }
    }
    
    public void generate(Command ast)
    {
        try {
            currentFunction = ActivationRecord.newGlobalFrame();
            ast.accept(this);
            hasError();
        } catch (CodeGenException e) {
        }
    }
    
    public Program getProgram()
    {
        return program;
    }

    private void printHelper(String prefix, ast.Command node) {
        program.appendInstruction("\t\t\t# "+prefix+" "+node.toString());
    }

    @Override
    public void visit(ExpressionList node) {
        printHelper("begin", node);

        for (Expression expression : node) {
            expression.accept(this);
        }
        printHelper("end", node);

    }

    @Override
    public void visit(DeclarationList node) {
        printHelper("begin", node);
        for (Declaration declaration : node) {
            declaration.accept(this);
        }
//        program.appendExitSequence();
        printHelper("end", node);
    }

    @Override
    public void visit(StatementList node) {
        printHelper("begin", node);
        for (Statement statement : node) {
            statement.accept(this);
            if (statement instanceof Call) {
                Type t = tc.getType((Command) statement);
                if (t instanceof IntType)
                    program.popInt("$t0");
                else if (t instanceof FloatType)
                    program.popFloat("$f0");
            }
        }
        printHelper("end", node);
    }

    @Override
    public void visit(AddressOf node) {
        printHelper("begin", node);

        Integer offset = currentFunction.getAddress(program,"$t0", node.symbol());
        if (offset == null) {
            throw new RuntimeException("AddressOf: Should never happened");
        }
        program.pushInt("$t0");

        printHelper("end", node);
    }

    @Override
    public void visit(LiteralBool node) {
        printHelper("begin", node);

        String instruction = "li  \t$t0, " +
                (node.value() == LiteralBool.Value.TRUE ? "1" : "0");
        program.appendInstruction(instruction);
        program.pushInt("$t0");

        printHelper("end", node);

    }

    @Override
    public void visit(LiteralFloat node) {
        printHelper("begin", node);

        program.appendInstruction("li.s\t$f0, "+node.value());
        program.pushFloat("$f0");

        printHelper("end", node);
    }

    @Override
    public void visit(LiteralInt node) {
        printHelper("begin", node);

        program.appendInstruction("li  \t$t0, "+node.value());
        program.pushInt("$t0");

        printHelper("end", node);
    }

    @Override
    public void visit(VariableDeclaration node) {
        printHelper("begin", node);
        currentFunction.add(program, node);
        printHelper("end", node);
    }

    @Override
    public void visit(ArrayDeclaration node) {
        printHelper("begin", node);
        currentFunction.add(program, node);
        printHelper("end", node);
    }

    @Override
    public void visit(FunctionDefinition node) {
        printHelper("begin", node);
        currentFunction = new ActivationRecord(node, currentFunction);

        String prefix = !node.symbol().name().equals("main") ? "func." : "";
        String instruction = prefix + node.symbol().name() + ":";
        int pos = program.appendInstruction(instruction);


        node.body().accept(this);
        program.insertPrologue(pos+1, currentFunction.stackSize());

        String epilogue = prefix+node.symbol().name()+".epilogue:";
        program.appendInstruction(epilogue);
        program.appendEpilogue(currentFunction.stackSize());
        currentFunction = currentFunction.parent();
        printHelper("end", node);
    }

    @Override
    public void visit(Addition node) {
        printHelper("begin", node);

        node.rightSide().accept(this);
        node.leftSide().accept(this);

        Type current = tc.getType(node);
        if (current instanceof IntType) {
            program.popInt("$t1");
            program.popInt("$t0");
            program.appendInstruction("addu\t$t0, $t1, $t0");
            program.pushInt("$t0");
        } else {
            program.popFloat("$f1");
            program.popFloat("$f0");
            program.appendInstruction("add.s\t$f0, $f1, $f0");
            program.pushFloat("$f0");
        }
        printHelper("end", node);
    }

    @Override
    public void visit(Subtraction node) {
        printHelper("begin", node);

        node.rightSide().accept(this);
        node.leftSide().accept(this);

        Type current = tc.getType(node);
        if (current instanceof IntType) {
            program.popInt("$t1");
            program.popInt("$t0");
            program.appendInstruction("subu\t$t0, $t1, $t0");
            program.pushInt("$t0");
        } else {
            program.popFloat("$f1");
            program.popFloat("$f0");
            program.appendInstruction("sub.s\t$f0, $f1, $f0");
            program.pushFloat("$f0");
        }
        printHelper("end", node);
    }

    @Override
    public void visit(Multiplication node) {
        printHelper("begin", node);

        node.rightSide().accept(this);
        node.leftSide().accept(this);
        Type current = tc.getType(node);
        if (current instanceof IntType) {
            program.popInt("$t1");
            program.popInt("$t0");
            program.appendInstruction("mult\t$t0, $t1");
            program.appendInstruction("mflo\t$t2");
            program.pushInt("$t2");
        } else {
            program.popFloat("$f1");
            program.popFloat("$f0");
            program.appendInstruction("mul.s\t$f0, $f1, $f0");
            program.pushFloat("$f0");
        }
        printHelper("end", node);
    }

    @Override
    public void visit(Division node) {
        printHelper("begin", node);

        node.rightSide().accept(this);
        node.leftSide().accept(this);
        Type current = tc.getType(node);
        if (current instanceof IntType) {
            program.popInt("$t1");
            program.popInt("$t0");
            program.appendInstruction("div \t$t0, $t1");
            program.appendInstruction("mflo\t$t2");
            program.pushInt("$t2");
        } else {
            program.popFloat("$f1");
            program.popFloat("$f0");
            program.appendInstruction("div.s\t$f0, $f1, $f0");
            program.pushFloat("$f0");
        }
        printHelper("end", node);    }

    @Override
    public void visit(LogicalAnd node) {
        printHelper("begin", node);

        node.rightSide().accept(this);
        node.leftSide().accept(this);
        program.popInt("$t1");
        program.popInt("$t0");

        program.appendInstruction("and \t$t2, $t0, $t1");
        program.pushInt("$t2");
        printHelper("end", node);
//        throw new RuntimeException("Implement this");
    }

    @Override
    public void visit(LogicalOr node) {
        printHelper("begin", node);

        node.rightSide().accept(this);
        node.leftSide().accept(this);
        program.popInt("$t0");
        program.popInt("$t1");

        program.popInt("or  \t $t2, $t0, $t1");
        program.pushInt("$t2");

        printHelper("end", node);
    }
    
    @Override
    public void visit(LogicalNot node) {
        printHelper("begin", node);

        node.expression().accept(this);
        program.popInt("$t0");

        program.appendInstruction("slti\t $t1, $t0, 1");
        program.pushInt("$t1");

        printHelper("end", node);
    }

    @Override
    public void visit(Comparison node) {
        printHelper("begin", node);

        node.rightSide().accept(this);
        node.leftSide().accept(this);


        if (tc.getType((Command) node.leftSide()) instanceof IntType) {
            program.popInt("$t0");
            program.popInt("$t1");

            program.appendInstruction("slt \t$t2, $t0, $t1");
            program.appendInstruction("xor \t$t3, $t0, $t1");
            program.appendInstruction("slti\t$t3, $t3, 1");

            switch (node.operation()) {
                case LT:
                    program.appendInstruction("addi\t$t5, $t2, 0");
                    break;
                case LE:
                    program.appendInstruction("or  \t$t5, $t2, $t3");
                    break;
                case EQ:
                    program.appendInstruction("addi\t$t5, $t3, 0");
                    break;
                case GE:
                    program.appendInstruction("slti\t$t5, $t2, 1");
                    break;
                case GT:
                    program.appendInstruction("add \t$t5, $t2, $t3");
                    program.appendInstruction("slti\t$t5, $t5, 1");
                    break;
                case NE:
                    program.appendInstruction("slti\t$t5, $t3, 1");
                    break;
            }
            program.pushInt("$t5");
        } else if (tc.getType((Command) node.leftSide()) instanceof FloatType) {
            program.popFloat("$f0");
            program.popFloat("$f1");
            switch (node.operation()) {
                case LT:
                    program.appendInstruction("c.lt.s\t$f0, $f1");
                    break;
                case LE:
                    program.appendInstruction("c.le.s\t$f0, $f1");
                    break;
                case EQ:
                    program.appendInstruction("c.eq.s\t$f0, $f1");
                    break;
                case GE:
                    program.appendInstruction("c.ge.s\t$f0, $f1");
                    break;
                case GT:
                    program.appendInstruction("c.gt.s\t$f0, $f1");
                    break;
                case NE:
                    program.appendInstruction("c.ne.s\t$f0, $f1");
                    break;
            }
            String falseLabel = program.newLabel();
            String ending = program.newLabel();
            program.appendInstruction("bc1f\t"+falseLabel);
            program.appendInstruction("addi\t$t0, $zero, 1");
            program.appendInstruction("j   \t"+ending);
            program.appendInstruction(falseLabel+":");
            program.appendInstruction("addi\t$t0, $zero, 0");
            program.appendInstruction(ending+":");
            program.pushInt("$t0");
        } else
            throw new RuntimeException();
        printHelper("end", node);
    }

    @Override
    public void visit(Dereference node) {
        printHelper("begin", node);

        node.expression().accept(this);
        program.popInt("$t0");
        if (tc.getType(node) instanceof IntType) {
            program.appendInstruction("lw  \t$t1,($t0)");
            program.pushInt("$t1");
        } else if (tc.getType(node) instanceof FloatType) {
            program.appendInstruction("l.s \t$f0, ($t0)");
            program.pushFloat("$f0");
        }

        printHelper("end", node);
    }

    @Override
    public void visit(Index node) {
        printHelper("begin", node);

        node.amount().accept(this);
        node.base().accept(this);
        program.popInt("$t1");
        program.popInt("$t0");


        program.appendInstruction("sll \t$t0, $t0, 2");
        program.appendInstruction("add \t$t1, $t0, $t1");
        program.pushInt("$t1");

        printHelper("end", node);
//        throw new RuntimeException("Implement this");
    }

    @Override
    public void visit(Assignment node) {
        printHelper("begin", node);

        node.source().accept(this);
        node.destination().accept(this);

        program.popInt("$t1");
        Type src = tc.getType((Command) node.source());
        if (src instanceof IntType) {
            program.popInt("$t0");
            program.appendInstruction("sw  \t$t0, 0($t1)");
        } else if (src instanceof FloatType) {
            program.popFloat("$f0");
            program.appendInstruction("s.s \t$f0, 0($t1)");
        } else
            throw new RuntimeException("Wrong type");

        printHelper("end", node);
    }

    @Override
    public void visit(Call node) {
        printHelper("begin", node);
        node.arguments().accept(this);

        program.appendInstruction("jal \tfunc."+node.function().name());

        FuncType funcType = (FuncType) node.function().type();
        program.appendInstruction("addi\t$sp, $sp, "+ActivationRecord.numBytes(funcType.arguments()));
        if (funcType.returnType() instanceof IntType) {
            program.appendInstruction("subu\t$sp, $sp, 4");
            program.appendInstruction("sw  \t$v0, 0($sp)");
        } else if (funcType.returnType() instanceof FloatType) {
            program.appendInstruction("subu\t$sp, $sp, 4");
            program.appendInstruction("s.s \t$f0, 0($sp)");
        }

        printHelper("end", node);

    }

    @Override
    public void visit(IfElseBranch node) {
        printHelper("begin", node);

        final String thenBranch = program.newLabel();
        final String elseBranch = program.newLabel();
        final String ending = program.newLabel();
        node.condition().accept(this);
        program.popInt("$t0");
        program.appendInstruction("beqz\t$t0, "+elseBranch);
        program.appendInstruction(thenBranch+":");
        node.thenBlock().accept(this);
        program.appendInstruction("j   \t"+ending);
        program.appendInstruction(elseBranch+":");
        node.elseBlock().accept(this);
        program.appendInstruction(ending+":");

        printHelper("end", node);
    }

    @Override
    public void visit(WhileLoop node) {
        printHelper("begin", node);

        String condition = program.newLabel();
        String ending = program.newLabel();

        program.appendInstruction(condition+":");
        node.condition().accept(this);
        program.popInt("$t0");
        program.appendInstruction("beqz\t$t0, "+ending);

        node.body().accept(this);
        program.appendInstruction("j   \t"+condition);
        program.appendInstruction(ending+":");
        printHelper("end", node);
    }

    @Override
    public void visit(Return node) {
        printHelper("begin", node);
        node.argument().accept(this);
        Type returnType = tc.getType((Command) node.argument());
        if (returnType instanceof FloatType)
            program.popFloat("$f0");
        else
            program.popInt("$v0");
        program.appendInstruction("j   \tfunc."+currentFunction.name()+".epilogue");
        printHelper("end", node);
    }

    @Override
    public void visit(ast.Error node) {
        String message = "CodeGen cannot compile a " + node;
        errorBuffer.append(message);
        throw new CodeGenException(message);
    }
}
