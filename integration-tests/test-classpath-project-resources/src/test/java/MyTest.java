import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MyTest {

    private final ClassWithFinalMethod testMock = mock(ClassWithFinalMethod.class);

    @Test
    public void testMockingFinalMethod() {
        String testedString = "overridden value";
        when(testMock.finalMethod()).thenReturn(testedString);
        String result = testMock.finalMethod();
        Assert.assertEquals(testedString, result);
    }
}

class ClassWithFinalMethod {
    String finalMethod() {
        return "final method result";
    }
}
