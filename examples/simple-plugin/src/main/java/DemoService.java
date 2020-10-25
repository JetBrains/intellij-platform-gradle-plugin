public class DemoService {
    public DemoService() {
        System.out.println("Service " + getClass().getClassLoader().getClass().getSimpleName());
        System.out.println("Loaded from " + getClass().getProtectionDomain().getCodeSource().getLocation());
    }
}
