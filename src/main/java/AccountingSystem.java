import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountingSystem implements AccountingSystemInterface {

    private ConcurrentHashMap<String, Account> registeredPhones;
    private ConcurrentHashMap<String, String> currentConnections;

    private ConcurrentCallMap billing;

    private List<String> awaitingCalls = Collections.synchronizedList(new ArrayList<>());

    private ExecutorService executorService;

    private ExecutorService automaticDisconnectionService;

    private final Object lock1;
    private final Object lock2;

    public AccountingSystem() {
        this.registeredPhones = new ConcurrentHashMap<>();
        this.currentConnections = new ConcurrentHashMap<>();

        this.executorService = Executors.newFixedThreadPool(100);

        this.automaticDisconnectionService = Executors.newFixedThreadPool(100);

        this.billing = new ConcurrentCallMap();

        this.lock1 = new Object();
        this.lock2 = new Object();

    }

    @Override
    public void phoneRegistration(String number, PhoneInterface phone) {
        this.registeredPhones.putIfAbsent(number, new Account(phone));
    }

    @Override
    public long subscriptionPurchase(String number, long time) {
        if (this.registeredPhones.isEmpty())
            return 0L;
        return this.registeredPhones.computeIfPresent(number, (n, p) -> {
            p.addTime(time);
            return p;
        }).getRemainingTime().orElse(0L);
    }

    @Override
    public Optional<Long> getRemainingTime(String number) {
        if (this.registeredPhones.isEmpty())
            return Optional.empty();
        return this.registeredPhones.computeIfPresent(number, (n, p) -> p).getRemainingTime();
    }

    @Override
    public boolean connection(String numberFrom, String numberTo) {
        synchronized (this.lock2) {
            if (!this.registeredPhones.containsKey(numberFrom) || !this.registeredPhones.containsKey(numberTo))
                return false;

            if (!this.registeredPhones.get(numberFrom).getRemainingTime().isPresent() || this.registeredPhones.get(numberFrom).getRemainingTime().get() < 0L)
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
                    this.startConnection(this.registeredPhones.get(numberFrom), numberFrom);
                    this.billing.put(numberFrom, numberTo);
                    awaitingCalls.remove(numberFrom);
                    awaitingCalls.remove(numberTo);
                    return true;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void disconnection(String number) {
        this.executorService.submit(() -> {
            synchronized (this.lock1) {
                if (this.currentConnections.containsKey(number) || this.currentConnections.containsValue(number)) {
                    String numberTo = this.currentConnections.get(number);
                    this.registeredPhones.get(number).stopConnection();
                    this.registeredPhones.get(number).getPhone().connectionClosed(numberTo);
                    this.registeredPhones.get(numberTo).getPhone().connectionClosed(number);
                    this.currentConnections.remove(number);
                    this.billing.put(number, numberTo, this.registeredPhones.get(number).getRemainingTime().get());
                }
            }
        });
    }

    @Override
    public Optional<Long> getBilling(String numberFrom, String numberTo) {
        return Optional.of(this.billing.getOrDefault(numberFrom, numberTo, 0L));
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

    private void startConnection(Account accountFrom, String number) {
        this.automaticDisconnectionService.submit(() -> {
            accountFrom.startConnection();
            while (!accountFrom.isForcefullyDisconnected() && !accountFrom.isConnectionClosed()) ;

            if (accountFrom.isConnectionClosed())
                disconnection(number);
        });
    }

    class Account {
        private PhoneInterface phone;
        private Long remainingTime;

        private final Object lock;

        private Thread thread;
        private volatile long startedAt;
        private volatile long closedAt;

        private volatile boolean isForcefullyDisconnected;
        private volatile boolean connectionClosed;
        private volatile long currentTime;

        private AtomicBoolean isRunning;

        public Account(PhoneInterface phone) {
            this.phone = phone;
            this.lock = new Object();

            this.isRunning = new AtomicBoolean(false);
        }

        public PhoneInterface getPhone() {
            return phone;
        }

        public Optional<Long> getRemainingTime() {
            return Optional.ofNullable(this.remainingTime);
        }

        public void addTime(Long time) {
            synchronized (this.lock) {
                if (this.remainingTime == null)
                    this.remainingTime = 0L;
                this.remainingTime += time;
            }
        }

        public void startConnection() {
            this.startedAt = this.getNano();
            this.isRunning.set(true);

            synchronized (this) {
                this.isForcefullyDisconnected = false;
                this.closedAt = 0L;
                this.connectionClosed = false;
            }
            this.thread = new Thread(() -> {

                while (this.isRunning.get()) {
                    currentTime = this.getNano();
                    if (currentTime - startedAt >= (remainingTime)) {
                        stopConnection();
                        this.isForcefullyDisconnected = true;
                    }
                }
            });
            this.thread.start();
        }

        public void stopConnection() {
            if (!isRunning.get())
                return;
            this.closedAt = this.getNano();
            this.connectionClosed = true;
            this.isRunning.set(false);
            try {
                this.thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.remainingTime = this.remainingTime - ((closedAt - startedAt));
            if (this.remainingTime < 0L)
                this.remainingTime = 0L;
        }

        public boolean isForcefullyDisconnected() {
            return isForcefullyDisconnected;
        }

        public boolean isConnectionClosed() {
            return connectionClosed;
        }

        private long getNano() {
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

        public String getNumberFrom() {
            return numberFrom;
        }

        public String getNumberTo() {
            return numberTo;
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
        public Long getOrDefault(String numberFrom, String numberTo, Long def) {
            CallHistory call = CallHistory.make(numberFrom, numberTo);
            return this.getOrDefault(call, def);
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
