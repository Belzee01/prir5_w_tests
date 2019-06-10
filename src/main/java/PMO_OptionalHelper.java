import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

public class PMO_OptionalHelper {
	public static <T> T testAndGet( Optional<T> o, boolean presentExpected ) {
		assertNotNull(o, "Zamiast Optional otrzymano null");
		if ( presentExpected ) {
			assertTrue( o.isPresent(), "Otrzymano pusty obiekt Optional");
		} else {
			assertTrue( o.isEmpty(), "Otrzymano niepusty obiekt Optional");			
		}
		return o.get();
	}
	
	public static <T> T testAndGet( Optional<T> o ) {
		return testAndGet(o, true);
	}
}
