package resources;

public class TestClass {

    public void nestedStatements(String arg) {
        int a = 0;
        if (a == 0) {
            a = 1;
            if (a == 1) {
                a = 2;
            }
        }
        for (int i = 0; i < 10; i++) {
            a += i;
        }
        while (a < 20) {
            a++;
        }
        try {
            a = 0;
        } catch (Exception e) {
            a = 1;
        }
        nestedStatements(Integer.toString(a));
        String s = new String();
    }

}
