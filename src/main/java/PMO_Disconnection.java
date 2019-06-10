
public class PMO_Disconnection implements Runnable {

	private final AccountingSystemInterface asi;
	private final String number;
	private final long duration;
	private volatile boolean exceptionCaught;
	private volatile boolean disconnectionExecuted;
	private volatile long disconnectedAt;

	public PMO_Disconnection(AccountingSystemInterface asi, String number, long duration ) {
		this.asi = asi;
		this.number = number;
		this.duration = duration;
	}

	@Override
	public void run() {
		PMO_LogSource.logS( "Uruchomino wątek, który wykona disconnect(" + number + ") za " + duration );
		PMO_TimeHelper.sleep( duration );
		try {
			PMO_LogSource.logS( "Za chwilę wykona się disconnect(" + number + ")" );
			disconnectionExecuted = true;
			asi.disconnection(number);
			disconnectedAt = PMO_TimeHelper.getMsec();
		} catch (Exception e) {
			exceptionCaught = true;
			PMO_LogSource.errorS("W PMO_Disconnection pojawił się wyjątek " + e.toString());
		}
	}

	public long getDisconnectedAt() {
		return disconnectedAt;
	}

	public boolean getDisconnectionExecuted() {
		return disconnectionExecuted;
	}

	public boolean getExceptionCaught() {
		return exceptionCaught;
	}
}
