import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Junkyard {
    static ExecutorService myExecutor = Executors.newCachedThreadPool(); //or whatever



    public static void main(String[] args) {
        myExecutor.execute(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(1);
            }
        });
        myExecutor.execute(new Runnable() {
            public void run() {
                System.out.println(2);
            }
        });
    }
}
