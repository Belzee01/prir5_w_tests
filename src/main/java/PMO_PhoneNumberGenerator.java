import java.util.concurrent.atomic.AtomicInteger;

public class PMO_PhoneNumberGenerator {
	private static final AtomicInteger counter = new AtomicInteger();
	private static final String PREFIX = "(48)(12)664";
	private static final String FORMAT = "%s-%06d";

	public static int getCurrentCounter() {
		return counter.get();
	}

	public static String getNumber(int counter) {
		return String.format(FORMAT, PREFIX, counter);
	}

	public static String getNumber() {
		return getNumber(counter.getAndIncrement());
	}
}
