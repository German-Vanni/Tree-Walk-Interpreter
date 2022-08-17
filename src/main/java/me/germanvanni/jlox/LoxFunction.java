package me.germanvanni.jlox;

import java.util.List;

class LoxFunction implements LoxCallable{

    private final Stmt.Function declaration;
    private final Environment closure;
    private final boolean isConstructor;

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isConstructor){

        this.declaration = declaration;
        this.closure = closure;
        this.isConstructor = isConstructor;
    }
    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment environment = new Environment(interpreter.globals);
        for(int i = 0; i < declaration.params.size(); i++){
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }

        try{
            interpreter.executeBlock(declaration.body, environment);
        }catch (Return returnValue){
            if(isConstructor) return closure.getAt(0, "this");

            return returnValue.value;
        }
        if(isConstructor) return closure.getAt(0, "this");
        return null;
    }

    LoxFunction bind(LoxInstance instace){
        Environment environment = new Environment(closure);
        environment.define("this", instace);
        return new LoxFunction(declaration, environment, isConstructor);
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
