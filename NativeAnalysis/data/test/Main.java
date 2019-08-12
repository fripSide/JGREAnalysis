abstract class Animal {
    public abstract void saySomething();
}
 
class Cat extends Animal {
    public void saySomething() {
        System.out.println("purr");
    }
}
 
class Dog extends Animal {
    public void saySomething() {
        System.out.println("woof");
    }
}
 
class Fish extends Animal {
    public void saySomething() {
        System.out.println("...");
    }
}
 
class Car {  // not an Animal
    public void saySomething() {
        System.out.println("honk!");
    }
}
 
public class Main {
	public int i1;
	public static float f1 = 0.1f;
	private boolean flag = false;
	static private Animal tmp;
	
    static Animal neverCalled() {
        return new Fish();
    }
 
    static Animal selectAnimal() {
        return new Cat();
    }
 
    public static void main(String[] args) {
        Animal animal = selectAnimal();
		tmp = animal;
        animal.saySomething();
		System.out.println(tmp);
    }
}