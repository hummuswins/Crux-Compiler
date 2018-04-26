package mips;

import java.util.HashMap;
import java.util.HashSet;

import crux.Symbol;
import types.*;

public class ActivationRecord
{
    private static int fixedFrameSize = 2*4;
    private ast.FunctionDefinition func;
    private ActivationRecord parent;
    private int stackSize;
    private HashMap<Symbol, Integer> locals;
    private HashMap<Symbol, Integer> arguments;
    
    public static ActivationRecord newGlobalFrame()
    {
        return new GlobalFrame();
    }
    
    protected static int numBytes(Type type)
    {
    	if (type instanceof BoolType)
    		return 4;
        if (type instanceof IntType)
            return 4;
        if (type instanceof FloatType)
            return 4;
        if (type instanceof ArrayType) {
            ArrayType aType = (ArrayType)type;
            return aType.extent() * numBytes(aType.base());
        }
        if (type instanceof TypeList) {
            int sum = 0;
            for (Type t : (TypeList) type) {
                sum += numBytes(t);
            }
            return sum;
        }
        if (type instanceof VoidType)
            return 0;
        throw new RuntimeException("No size known for " + type);
    }
    
    protected ActivationRecord()
    {
        this.func = null;
        this.parent = null;
        this.stackSize = 0;
        this.locals = null;
        this.arguments = null;
    }
    
    public ActivationRecord(ast.FunctionDefinition fd, ActivationRecord parent)
    {
        this.func = fd;
        this.parent = parent;
        this.stackSize = 0;
        this.locals = new HashMap<>();
        
        // map this function's parameters
        this.arguments = new HashMap<>();
        int offset = 0;
        for (int i=fd.arguments().size()-1; i>=0; --i) {
            Symbol arg = fd.arguments().get(i);
            arguments.put(arg, offset);
            offset += numBytes(arg.type());
        }
    }
    
    public String name()
    {
        return func.symbol().name();
    }
    
    public ActivationRecord parent()
    {
        return parent;
    }
    
    public int stackSize()
    {
        return stackSize;
    }
    
    public void add(Program prog, ast.VariableDeclaration var)
    {
        locals.put(var.symbol(), stackSize);
        stackSize += numBytes(var.symbol().type());
    }
    
    public void add(Program prog, ast.ArrayDeclaration array)
    {
//        throw new RuntimeException("implement adding array to local function space");
        locals.put(array.symbol(), stackSize);
        stackSize += numBytes(array.symbol().type());
    }
    
    public Integer getAddress(Program prog, String reg, Symbol sym)
    {
//        throw new RuntimeException("implement accessing address of local or parameter symbol");
        Integer address = locals.get(sym);
        if (address != null) {
            prog.appendInstruction("la  \t"+reg+", "+String.valueOf(-12-address)+"($fp)");
            return address;
        }

        address = arguments.get(sym);
        if (address != null) {
            prog.appendInstruction("la  \t"+reg+", "+String.valueOf(address)+"($fp)");
            return address;
        }
        if (parent != null) {
            return parent.getAddress(prog, reg, sym);
        }
        return null;
    }
}

class GlobalFrame extends ActivationRecord
{
    private HashSet<String> dataAddr;
    public GlobalFrame()
    {
        dataAddr = new HashSet<>();
    }
    
    private String mangleDataname(String name)
    {
        return "cruxdata." + name;
    }
    
    @Override
    public void add(Program prog, ast.VariableDeclaration var)
    {
//        throw new RuntimeException("implement adding variable to global data space");
        String name = mangleDataname(var.symbol().name());
        dataAddr.add(var.symbol().name());
        prog.appendData(name + ":\t\t.space\t4");
    }    
    
    @Override
    public void add(Program prog, ast.ArrayDeclaration array)
    {
//        throw new RuntimeException("implement adding array to global data space");
        String name = mangleDataname(array.symbol().name());
        dataAddr.add(array.symbol().name());
        prog.appendData(name+":\t\t.space\t"+numBytes(array.symbol().type()));
    }
        
    @Override
    public Integer getAddress(Program prog, String reg, Symbol sym)
    {
        if (dataAddr.contains(sym.name())) {
            prog.appendInstruction("la  \t"+reg+", "+mangleDataname(sym.name()));
            return 0;
        }
        return null;
    }
}
