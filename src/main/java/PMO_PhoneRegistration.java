import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

public class PMO_PhoneRegistration implements Runnable {
	private final String number;
	private final AccountingSystemInterface asi;
	private final PhoneInterface pi;
	private final AtomicBoolean executed;

	PMO_PhoneRegistration(String number, AccountingSystemInterface asi, PhoneInterface pi) {
		this.number = number;
		this.asi = asi;
		this.pi = pi;
		executed = new AtomicBoolean();
	}

	public String getNumber() {
		return number;
	}
	
	@Override
	public void run() {
		PMO_TestHelper.tryToExecute(() -> {
			asi.phoneRegistration(number, pi);
			executed.set(true);
			PMO_LogSource.logS("Zarejestrowano telefon " + number );
		}, "phoneRegistration", PMO_Consts.PARALLEL_REGISTRATION_TIMEOUT);
	}

	public void test() {
		assertTrue(executed.get(),
				"Oczekiwano, że metoda phoneRegistration dla numeru " + number + " zostanie wykonana");
	}

}
