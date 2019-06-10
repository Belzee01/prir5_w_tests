
public class PMO_PhoneCall implements Runnable {
	private final String source;
	private final PMO_PhoneInterfaceImplementation sourcePhone;
	private final String destination;
	private final PMO_PhoneInterfaceImplementation destinationPhone;
	private final long duration;
	private final AccountingSystemInterface asi;
	private volatile boolean connectionExecuted;
	private volatile long connectionStartedAt;
	private volatile boolean sourceIsConnectedBefore;
	private volatile boolean destinationIsConnectedBefore;
	private volatile boolean sourceIsConnectedAfter;
	private volatile boolean destinationIsConnectedAfter;
	private volatile boolean connectionResult;
	private volatile PMO_Disconnection disconnection;
	private final String numbers;

	public PMO_PhoneCall(String source, String destination, PMO_PhoneInterfaceImplementation sourcePhone,
			PMO_PhoneInterfaceImplementation destinationPhone, long duration, AccountingSystemInterface asi) {
		this.source = source;
		this.destination = destination;
		this.duration = duration;
		this.sourcePhone = sourcePhone;
		this.destinationPhone = destinationPhone;
		this.asi = asi;
		disconnection = new PMO_Disconnection(asi, source, duration);
		numbers = source + " -> " + destination + " T: " + duration;
	}

	public PMO_PhoneCall(String source, String destination, long duration, AccountingSystemInterface asi) {
		this(source, destination, null, null, duration, asi);
	}

	@Override
	public void run() {
		connectionExecuted = true;

		PMO_LogSource.logS("Zgłaszam do systemu połączenie " + numbers);
		sourceIsConnectedBefore = PMO_OptionalHelper.testAndGet(asi.isConnected(source));
		destinationIsConnectedBefore = PMO_OptionalHelper.testAndGet(asi.isConnected(destination));

		connectionResult = asi.connection(source, destination);

		if (connectionResult) {
			PMO_LogSource.logS("Połączenie " + numbers + " zostało zaakceptowane przez odbierającego");
			connectionStartedAt = PMO_TimeHelper.getMsec();
		} else {
			PMO_LogSource.logS("Połączenie " + numbers + " NIE zostało zaakceptowane przez odbierającego");
		}
		sourceIsConnectedAfter = PMO_OptionalHelper.testAndGet(asi.isConnected(source));
		destinationIsConnectedAfter = PMO_OptionalHelper.testAndGet(asi.isConnected(destination));
		PMO_LogSource.logS("Zakończono metodę connection(" + numbers + ") wynik " + connectionResult);

		if (duration > 0) {
			PMO_LogSource
					.logS("Uruchamiam wątek, który za " + duration + " wykona disconnection połączenia " + numbers);
			PMO_ThreadsHelper.createThreadAndStartAsDaemon(disconnection);
		} else {
			PMO_LogSource.logS("Połączenie " + numbers + " nie ma mechanizmu rozłączającego");
		}
	}

	public long getConnectionStartedAt() {
		return connectionStartedAt;
	}

	public long getDisconnectedAt() {
		return sourcePhone.connectionClosedAt();
	}

	public String getSource() {
		return source;
	}

	public String getDestination() {
		return destination;
	}

	public String getNumbers() {
		return numbers;
	}
}
