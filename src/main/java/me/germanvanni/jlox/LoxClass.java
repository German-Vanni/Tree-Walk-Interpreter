package me.germanvanni.jlox;

import java.util.List;
import java.util.Map;

class LoxClass implements LoxCallable{
    final String name;
    final LoxClass superClass;
    final Map<String, LoxFunction> methods;

    LoxClass(String name, LoxClass superClass, Map<String, LoxFunction> methods){
        this.name = name;
        this.methods = methods;
        this.superClass = superClass;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction ctor = findMethod("ctor");
        if(ctor != null){
            ctor.bind(instance).call(interpreter, arguments);
        }

        return instance;
    }

    @Override
    public int arity() {

        LoxFunction ctor = findMethod("ctor");
        if(ctor == null) return 0;
        return ctor.arity();
    }

    LoxFunction findMethod(String name){
        if(methods.containsKey(name)){
            return methods.get(name);
        }
        if(superClass != null){
            return superClass.findMethod(name);
        }

        return null;
    }
}
