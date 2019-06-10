import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;

public class PMO_InstanceHelper {
	public static Object fabric(String className, String interfaceName) {
		try {
			if (Class.forName(interfaceName).isAssignableFrom(Class.forName(className))) {
				return Class.forName(className).getDeclaredConstructor().newInstance();
			} else {
				fail("Klasa nie jest zgodna z oczekiwanym interfejsem");
			}
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
			fail("W trakcie tworzenia obiektu klasy " + className + " pojawil sie wyjatek " + e.toString() + "\n"
					+ stackTrace2String(e));
		} catch (Exception e) {
			fail("W trakcie tworzenia obiektu klasy " + className + " nieoczekiwanie pojawil sie wyjatek "
					+ e.toString() + "\n" + stackTrace2String(e));
		}
		java.lang.System.exit(1);
		return null; // ta linia jest dlatego, że kompilator nie wie, ze wczesniejsza wylaczy JVM
	}

	public static String stackTrace2String(Throwable e) {
		StringWriter errors = new StringWriter();
		e.printStackTrace(new PrintWriter(errors));
		return errors.toString();
	}


}
