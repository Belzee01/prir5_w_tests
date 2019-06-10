import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class PMO_BarrierHelper {

	public static void await(CyclicBarrier barrier) {
		if (barrier != null) {
			try {
				barrier.await();
			} catch (InterruptedException | BrokenBarrierException e) {
				e.printStackTrace();
			}
		}
	}

}
