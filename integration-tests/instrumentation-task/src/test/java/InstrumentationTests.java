import org.junit.Test;

public class InstrumentationTests {

  @Test
  public void test() {
    Foo.foo(null);
  }

  @Test
  public void fooTest() {
    TestFoo.testFoo(null);
  }
}
