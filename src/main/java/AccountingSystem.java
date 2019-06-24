import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountingSystem implements AccountingSystemInterface {

    private ConcurrentHashMap<String, Account> registeredPhones;
    private ConcurrentHashMap<String, String> currentConnections;

    private ConcurrentCallMap billing;

    private List<String> awaitingCalls = Collections.synchronizedList(new ArrayList<>());

    private ExecutorService executorService;

    private final Object lock1;
    private final Object lock2;
    private final Object lock3;

    public AccountingSystem() {
        this.registeredPhones = new ConcurrentHashMap<>();
        this.currentConnections = new ConcurrentHashMap<>();

        this.executorService = new ThreadPoolExecutor(66, 100,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());

        this.billing = new ConcurrentCallMap();

        this.lock1 = new Object();
        this.lock2 = new Object();
        this.lock3 = new Object();
    }

    @Override
    public void phoneRegistration(String number, PhoneInterface phone) {
        this.registeredPhones.put(number, new Account(phone));
    }

    @Override
    public long subscriptionPurchase(String number, long time) {
        if (this.registeredPhones.isEmpty())
            return 0L;
        Account account = this.registeredPhones.get(number);
        if (account != null)
            return account.addTime(time);
        else
            return 0;
    }

    @Override
    public Optional<Long> getRemainingTime(String number) {
        if (this.registeredPhones.isEmpty())
            return Optional.empty();
        return Optional.ofNullable(this.registeredPhones.get(number).getRemainingTime());
    }

    @Override
    public boolean connection(String numberFrom, String numberTo) {
        synchronized (this.lock2) {
            if (!this.registeredPhones.containsKey(numberFrom) || !this.registeredPhones.containsKey(numberTo))
                return false;

            if ( this.registeredPhones.get(numberFrom).getRemainingTime() <= 0L)
                return false;
        }
        Future<Boolean> fromCallResult = null;

        synchronized (this.lock1) {
            if (!this.currentConnections.containsKey(numberTo) && !this.currentConnections.containsValue(numberTo)) {
                Callable<Boolean> fromCall = () -> this.registeredPhones.get(numberTo).getPhone().newConnection(numberFrom);
                if (awaitingCalls.contains(numberTo) || awaitingCalls.contains(numberFrom))
                    return false;

                fromCallResult = executorService.submit(fromCall);
                awaitingCalls.add(numberFrom);
                awaitingCalls.add(numberTo);
            }
        }

        try {
            if (fromCallResult.get()) {
                synchronized (this.lock1) {
                    if (!this.currentConnections.containsKey(numberTo) &&
                            !this.currentConnections.containsValue(numberTo) &&
                            !this.currentConnections.containsKey(numberFrom) &&
                            !this.currentConnections.containsValue(numberFrom)
                    )
                        this.currentConnections.put(numberFrom, numberTo);

                    synchronized (this.lock3) {
                        this.registeredPhones.get(numberFrom).startConnection(numberFrom, numberTo);
                        this.billing.put(numberFrom, numberTo);
                        awaitingCalls.remove(numberFrom);
                        awaitingCalls.remove(numberTo);
                        return true;
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void disconnection(String number) {
        synchronized (this.lock1) {
            if (this.currentConnections.containsKey(number) || this.currentConnections.containsValue(number)) {
                String numberTo = this.currentConnections.get(number);
                this.registeredPhones.get(number).stopConnection();
                this.registeredPhones.get(numberTo).stopConnection();

                this.registeredPhones.get(number).getPhone().connectionClosed(numberTo);
                this.registeredPhones.get(numberTo).getPhone().connectionClosed(number);
                this.currentConnections.remove(number);
                this.billing.put(number, numberTo, this.registeredPhones.get(number).getRemainingTime());
            }
        }
    }

    @Override
    public Optional<Long> getBilling(String numberFrom, String numberTo) {
        return Optional.ofNullable(this.billing.get(numberFrom, numberTo));
    }

    @Override
    public Optional<Boolean> isConnected(String number) {
        if (this.currentConnections.containsKey(number) || this.currentConnections.containsValue(number))
            return Optional.of(true);
        else if (!this.registeredPhones.containsKey(number))
            return Optional.empty();
        else
            return Optional.of(false);
    }

    class Account {
        private PhoneInterface phone;
        private Long remainingTime;

        private Thread thread;
        private volatile long closedAt;
        private volatile long currentTime;

        private AtomicBoolean isRunning;

        public Account(PhoneInterface phone) {
            this.phone = phone;
            this.isRunning = new AtomicBoolean(false);
        }

        public PhoneInterface getPhone() {
            return phone;
        }

        public Long getRemainingTime() {
            return this.remainingTime;
        }

        public Long addTime(Long time) {
            if (this.remainingTime == null)
                this.remainingTime = 0L;
            this.remainingTime += time;
            return this.remainingTime;
        }

        public void startConnection(String numberFrom, String numberTo) {
            this.isRunning.set(true);

            synchronized (this) {
                this.closedAt = 0L;
            }
            this.thread = new Thread(() -> {

                long startedAt = this.getMilli();
                while (this.isRunning.get()) {
                    currentTime = this.getMilli();
                    if (currentTime - startedAt >= (remainingTime)) {
                        this.closedAt = this.getMilli();
                        this.isRunning.set(false);
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                this.remainingTime = this.remainingTime - ((closedAt - startedAt));

                if (this.remainingTime < 0L)
                    this.remainingTime = 0L;

                disconnection(numberFrom);
                disconnection(numberTo);
                this.isRunning.set(false);
            });
            this.thread.start();
        }

        private void stopConnection() {
            this.closedAt = this.getMilli();
            this.isRunning.set(false);
        }

        private long getMilli() {
            return System.currentTimeMillis();
        }
    }

    public static class CallHistory {
        private String numberFrom;
        private String numberTo;

        public CallHistory(String numberFrom, String numberTo) {
            this.numberFrom = numberFrom;
            this.numberTo = numberTo;
        }

        public static CallHistory make(String numberFrom, String numberTo) {
            return new CallHistory(numberFrom, numberTo);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CallHistory that = (CallHistory) o;
            return Objects.equals(numberFrom, that.numberFrom) &&
                    Objects.equals(numberTo, that.numberTo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(numberFrom, numberTo);
        }
    }

    class ConcurrentCallMap extends ConcurrentHashMap<CallHistory, Long> {
        public Long get(String numberFrom, String numberTo) {
            CallHistory call = CallHistory.make(numberFrom, numberTo);
            return this.get(call);
        }

        public void put(String numberFrom, String numberTo) {
            CallHistory call = CallHistory.make(numberFrom, numberTo);
            Long currentBilling = 0L;
            if (this.contains(call)) {
                currentBilling = this.get(call);
            }
            this.putIfAbsent(call, currentBilling);
        }

        public void put(String numberFrom, String numberTo, Long duration) {
            CallHistory call = CallHistory.make(numberFrom, numberTo);
            Long currentBilling = 0L;
            if (this.contains(call)) {
                currentBilling = this.get(call);
            }
            this.put(call, currentBilling + duration);
        }
    }
}