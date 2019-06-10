import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Wersja 2019-05-21
 */
public class PMO_ThreadsHelper implements PMO_LogSource {

	static {
		PMO_LogSource.logS("PMO_ThreadsHelper ready");
	}

	public static String getThreadName() {
		return getCurrentThread().getName();
	}

	public static String getThreadName(Thread th) {
		return th.getName();
	}

	public static Thread getCurrentThread() {
		return Thread.currentThread();
	}

	public static void joinThreads(List<Thread> ths) {
		ths.forEach((t) -> {
			try {
				t.join();
			} catch (InterruptedException ie) {
				PMO_LogSource.logS("Doszlo do wyjatku w trakcie join " + ie);
			}
		});
	}

	public static Thread createThreadAndStartAsDaemon(Runnable task) {
		Thread th = new Thread(task);
		th.setDaemon(true);
		th.start();
		return th;
	}

	public static List<Thread> createAndStartThreads(Collection<? extends Runnable> tasks, boolean daemon) {
		List<Thread> result = new ArrayList<>();

		tasks.forEach(t -> {
			result.add(new Thread(t));
		});

		if (daemon) {
			result.forEach(t -> t.setDaemon(true));
		}

		result.forEach(t -> t.start());

		return result;
	}

	public static void showThreads(Set<Thread> threadSet) {
		threadSet.forEach((th) -> {
			System.out.println(PMO_ThreadsHelper.thread2String(th));
		});
	}

	public static void showThreads() {
		showThreads(Thread.getAllStackTraces().keySet());
	}

	public static Set<Thread> getThreads() {
		return Thread.getAllStackTraces().keySet();
	}

	public static String thread2String() {
		return thread2String(getCurrentThread());
	}

	public static String stackTraceElement2String(StackTraceElement element) {
		StringBuilder sb = new StringBuilder();

		sb.append(" Class: ");
		sb.append(element.getClassName());
		sb.append(element.isNativeMethod() ? " Native method: " : " Java method: ");
		sb.append(element.getMethodName());
		sb.append(" @");
		sb.append(element.getFileName());
		if (!element.isNativeMethod()) {
			sb.append("@");
			sb.append(element.getLineNumber());
		}

		return sb.toString();
	}

	public static String thread2String(Thread thread) {
		return thread2String(thread, thread.getStackTrace());
	}

	public static String thread2String(Thread thread, StackTraceElement[] stet) {
		StringBuilder sb = new StringBuilder();

		String threadName = "Thread: " + thread.getName();
		sb.append("Thread > ");
		sb.append(threadName);
		sb.append("\n");

		if (thread.isAlive()) {
			Thread.State state = thread.getState();
			sb.append(threadName);
			sb.append(" State ");
			sb.append(state.name());
			sb.append("\n");
			for (StackTraceElement ste : stet) {
				sb.append(threadName);
				sb.append(stackTraceElement2String(ste));
				sb.append("\n");
			}
		} else {
			sb.append(threadName);
			sb.append("is not alive\n");
		}

		return sb.toString();
	}

	public static boolean wait(Object o) {
		if (o != null) {
			synchronized (o) {
				try {
					o.wait();
					return true;
				} catch (InterruptedException ie) {
					PMO_LogSource.logS("Doszlo do wyjatku w trakcie wait" + ie);
					return false;
				}
			}
		}
		return true;
	}

	public static Runnable preBarrierWrapper(Runnable code, PMO_Barrier barrier) {
		return () -> {
			barrier.await();
			code.run();
		};
	}

	public static Runnable postBarrierWrapper(Runnable code, PMO_Barrier barrier) {
		return () -> {
			code.run();
			barrier.await();
		};
	}

}
