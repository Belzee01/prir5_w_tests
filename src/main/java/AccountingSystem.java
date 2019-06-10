import java.util.Optional;
import java.util.concurrent.*;

public class AccountingSystem implements AccountingSystemInterface {

    private ConcurrentHashMap<String, PhoneAndTime> registeredPhones;
    private ConcurrentHashMap<String, String> currentConnections;

    private ExecutorService executorService;

    private final Object lock;

    public AccountingSystem() {
        this.registeredPhones = new ConcurrentHashMap<>();
        this.currentConnections = new ConcurrentHashMap<>();

        this.executorService = Executors.newFixedThreadPool(100);

        this.lock = new Object();
    }

    @Override
    public void phoneRegistration(String number, PhoneInterface phone) {
        this.registeredPhones.putIfAbsent(number, new PhoneAndTime(phone));
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
        synchronized (this.lock) {
            if (!this.registeredPhones.containsKey(numberFrom) || !this.registeredPhones.containsKey(numberTo))
                return false;

            if (!this.registeredPhones.get(numberFrom).getRemainingTime().isPresent() || this.registeredPhones.get(numberFrom).getRemainingTime().get() < 0L)
                return false;
        }

        if (!this.currentConnections.containsKey(numberTo) && !this.currentConnections.containsValue(numberTo)) {
            Callable<Boolean> fromCall = () -> {
                System.out.println("Supplying connection from " + numberFrom + " to :" + numberTo);
                return this.registeredPhones.get(numberFrom).getPhone().newConnection(numberTo);
            };
            Callable<Boolean> toCall = () -> {
                System.out.println("Supplying connection from " + numberFrom + " to :" + numberTo);
                return this.registeredPhones.get(numberTo).getPhone().newConnection(numberFrom);
            };

            Future<Boolean> fromCallResult = executorService.submit(fromCall);
            Future<Boolean> toCallResult = executorService.submit(toCall);

            try {
                if (fromCallResult.get() && toCallResult.get()) {
                    synchronized (this) {
                        if (!this.currentConnections.containsKey(numberTo) &&
                                !this.currentConnections.containsValue(numberTo) &&
                                !this.currentConnections.containsKey(numberFrom) &&
                                !this.currentConnections.containsValue(numberFrom)
                        )
                            this.currentConnections.put(numberFrom, numberTo);
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public void disconnection(String number) {

    }

    @Override
    public Optional<Long> getBilling(String numberFrom, String numberTo) {
        return Optional.empty();
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

    class PhoneAndTime {
        private PhoneInterface phone;
        private Long remainingTime;

        private final Object lock;

        public PhoneAndTime(PhoneInterface phone) {
            this.phone = phone;
            this.lock = new Object();
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
    }
}
