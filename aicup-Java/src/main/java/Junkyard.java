import java.util.ArrayList;

public class Junkyard {


    public static void main(String[] args) {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(32);
        list.add(3);
        list.add(75);
        list.add(1);
        int a = list.get(0);
        list.set(0, list.get(2));
        list.set(2,a);
        System.out.println(list);
    }
}
