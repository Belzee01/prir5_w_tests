import static org.junit.jupiter.api.Assertions.assertTrue;

public class PMO_Purchase implements Runnable {
	private final AccountingSystemInterface asi;
	private final String number;
	private final long time;
	private volatile boolean purchaseExecuted;

	public PMO_Purchase(AccountingSystemInterface asi, String number, long time) {
		this.asi = asi;
		this.number = number;
		this.time = time;
	}

	@Override
	public void run() {
		PMO_TestHelper.tryToExecute(() -> {
			long singlePurchase = time / PMO_Consts.PARALLEL_PURCHASES_IN_ONE_THREAD;
			for (int i = 0; i < PMO_Consts.PARALLEL_PURCHASES_IN_ONE_THREAD; i++) {
				long remainingTimeAfterPurchase = asi.subscriptionPurchase(number, singlePurchase);
//				PMO_LogSource.logS("Dla telefonu " + number + " wykupiono " + singlePurchase + " msec"
//						+ " remaining time = " + remainingTimeAfterPurchase);
			}
			purchaseExecuted = true;
			PMO_LogSource.logS("Koniec zakupów dla telefonu " + number);

		}, "subscriptionPurchase", time);
	}

	public boolean purcheseExecuted() {
		return purchaseExecuted;
	}

	public void test() {
		assertTrue(purchaseExecuted,
				"Oczekiwano, że metoda phoneRegistration dla numeru " + number + " zostanie wykonana");
	}

}
