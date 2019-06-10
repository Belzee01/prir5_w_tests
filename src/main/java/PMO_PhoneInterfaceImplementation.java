import java.util.concurrent.atomic.AtomicLong;

public class PMO_PhoneInterfaceImplementation implements PhoneInterface {

	private volatile boolean connectionClosedExecuted;
	private volatile boolean newConnectionExecuted;
	private final PMO_Barrier newConnectionBarrier;
	private final AtomicLong connectionClosedAt;
	private volatile String connectionTo;
	private final String number;
	private final boolean accept;

	public PMO_PhoneInterfaceImplementation(PMO_Barrier newConnectionBarrier, String number, boolean accept ) {
		this.newConnectionBarrier = newConnectionBarrier;
		this.connectionClosedAt = new AtomicLong();
		this.number = number;
		this.accept = accept;
	}

	public PMO_PhoneInterfaceImplementation(PMO_Barrier newConnectionBarrier, String number) {
		this(newConnectionBarrier, number, false );
	}

	public PMO_PhoneInterfaceImplementation() {
		this(new PMO_Barrier(1, false, false, false, "local barrier") );
	}

	public PMO_PhoneInterfaceImplementation(PMO_Barrier newConnectionBarrier) {
		this(newConnectionBarrier, "unknown", false );
	}


	@Override
	public void connectionClosed(String number) {
		connectionClosedExecuted = true;
		connectionClosedAt.set(PMO_TimeHelper.getMsec());
		PMO_LogSource.logS( "Wykonana została metoda connectionClosed z argumentem " + number); 
	}

	@Override
	public boolean newConnection(String number) {
		newConnectionExecuted = true;
		connectionTo = number;
		PMO_LogSource.logS("Wywołano newConnection z " + number + " jeszcze nie podjęto decyzji co zrobić");
		newConnectionBarrier.await();
		PMO_LogSource.logS("Wywołano newConnection z " + number + " za chwilę połączenie zostanie "
				+ (accept ? "zaakceptowane" : "odrzucone"));
		return accept;
	}

	public boolean connectionClosedExecuted() {
		return connectionClosedExecuted;
	}

	public boolean newConnectionExecuted() {
		return newConnectionExecuted;
	}

	public long connectionClosedAt() {
		return connectionClosedAt.get();
	}

	public String connectionTo() {
		return connectionTo;
	}

	public String getNumber() {
		return number;
	}
}
