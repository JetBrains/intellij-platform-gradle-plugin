public class DemoComponent {
    private final DemoService service;

    public DemoComponent(DemoService service) {
        System.out.println("Component " + getClass().getClassLoader().getClass().getSimpleName());
        System.out.println("Loaded from " + getClass().getProtectionDomain().getCodeSource().getLocation());
        this.service = service;
    }

    public DemoService getService() {
        return service;
    }
}
