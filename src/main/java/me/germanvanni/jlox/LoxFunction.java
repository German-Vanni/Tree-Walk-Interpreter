package me.germanvanni.jlox;

import java.util.List;

class LoxFunction implements LoxCallable{

    private final Stmt.Function declaration;
    private final Environment closure;
    LoxFunction(Stmt.Function declaration, Environment closure){

        this.declaration = declaration;
        this.closure = closure;
    }
    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment enviroment = new Environment(interpreter.globals);
        for(int i = 0; i < declaration.params.size(); i++){
            enviroment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }

        try{
            interpreter.executeBlock(declaration.body, enviroment);
        }catch (Return returnValue){
            return returnValue.value;
        }

        return null;
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
