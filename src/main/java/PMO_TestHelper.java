import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;

import org.junit.jupiter.api.function.ThrowingSupplier;

public class PMO_TestHelper {
	public static void showException(Throwable e, String txt) {
		e.printStackTrace();
		fail("W trakcie pracy metody " + txt + " doszło do wyjątku " + e.toString());
	}

	////////////////////////////////////////////////////////////////////////////////

	public static <T> T tryToExecute(ThrowingSupplier<T> run, String txt, long timeLimit) {
		try {
			return assertTimeoutPreemptively(Duration.ofMillis(timeLimit), run::get, "Przekroczono limit czasu " + txt);
		} catch (Exception e) {
			showException(e, txt);
		}
		return null;
	}

	public static void tryToExecute(Runnable run, String txt, long timeLimit) {
		try {
			assertTimeoutPreemptively(Duration.ofMillis(timeLimit), run::run, "Przekroczono limit czasu " + txt);
		} catch (Exception e) {
			showException(e, txt);
		}
	}

}
