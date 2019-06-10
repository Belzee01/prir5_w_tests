import org.junit.jupiter.api.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class PMO_Test {
	//////////////////////////////////////////////////////////////////////////
	private static final Map<String, Double> tariff = new HashMap<>();

	@Retention(RetentionPolicy.RUNTIME)
	public static @interface Tariff {
		double value();
	}

	static {
	}

	public static double getTariff(String testName) {
		return tariff.get(testName);
	}

	//////////////////////////////////////////////////////////////////////////

	private AccountingSystemInterface asi;

	@BeforeEach
	public void create() {
		asi = (AccountingSystemInterface) PMO_InstanceHelper.fabric("AccountingSystem", "AccountingSystemInterface");
	}

	@Test
	@Tariff(3.0)
	// test początkowego stanu bilingu przed rejestracją telefonu
	public void beforeRegistrationBilling() {
		Optional<Long> value = PMO_TestHelper.tryToExecute(() -> asi.getBilling("dddaaa", "kda"), "getBilling", 100);

		assertNotNull(value, "Wynik metody getBilling nigdy nie może być null");
		assertTrue(value.isEmpty(), "Wynik getBilling dla niezarejestrowanego telefonu to pusty obiekt Optional");
	}

	@Test
	@Tariff(3.0)
	// test początkowego stanu czasu połączeń przed rejestracją telefonu
	public void beforeRegistrationRemaining() {
		Optional<Long> value = PMO_TestHelper.tryToExecute(() -> asi.getRemainingTime("dddaaa"), "getRemainingTime",
				100);

		assertNotNull(value, "Wynik metody getRemainingTime nigdy nie może być null");
		assertTrue(value.isEmpty(), "Wynik getRemainingTime dla niezarejestrowanego telefonu to pusty obiekt Optional");
	}

	@Test
	@Tariff(3.0)
	// test początkowego stanu połączenia przed rejestracją telefonu
	public void beforeRegistrationIsConnected() {
		Optional<Boolean> value = PMO_TestHelper.tryToExecute(() -> asi.isConnected("ddaa"), "isConnected", 100);

		assertNotNull(value, "Wynik metody isConnected nigdy nie może być null");
		assertTrue(value.isEmpty(), "Wynik isConnected dla niezarejestrowanego telefonu to pusty obiekt Optional");
	}

	private void testStateAfterRegistration(String number) {
		Optional<Boolean> value = PMO_TestHelper.tryToExecute(() -> asi.isConnected("ddaa"), "isConnected", 100);

		assertNotNull(value, "Wynik metody isConnected nigdy nie może być null");
		assertTrue(value.isEmpty(), "Wynik isConnected dla zarejestrowanego telefonu to NIEpusty obiekt Optional");
	}

	private List<PMO_PhoneRegistration> generatePhones(final int phones, final PMO_PhoneInterfaceImplementation pi) {
		List<PMO_PhoneRegistration> pack = new ArrayList<>(phones);
		IntStream.range(0, phones)
				.forEach(i -> pack.add(new PMO_PhoneRegistration(PMO_PhoneNumberGenerator.getNumber(), asi, pi)));
		return pack;
	}

	private Map<String, PMO_PhoneInterfaceImplementation> generatePhoneInterfaceMap(List<String> phoneNumbers,
			PMO_Barrier newConnectionBarrier, boolean answer) {
		Map<String, PMO_PhoneInterfaceImplementation> phoneMap = new TreeMap<>();
		phoneNumbers
				.forEach(p -> phoneMap.put(p, new PMO_PhoneInterfaceImplementation(newConnectionBarrier, p, answer)));
		return phoneMap;
	}

	private List<PMO_PhoneRegistration> generatePhoneRegistration(
			Map<String, PMO_PhoneInterfaceImplementation> phones) {
		return phones.entrySet().stream().map(e -> new PMO_PhoneRegistration(e.getKey(), asi, e.getValue()))
				.collect(Collectors.toList());
	}

	private List<PMO_Purchase> generatePurchase(Map<String, PMO_PhoneInterfaceImplementation> phones, long value) {
		return phones.entrySet().stream().map(e -> new PMO_Purchase(asi, e.getKey(), value))
				.collect(Collectors.toList());
	}

	private void executeWithTimeout(List<? extends Runnable> tasks, long timeout) {
		assertTimeoutPreemptively(Duration.ofMillis(timeout), () -> {
			tasks.forEach(t -> t.run());
		});
	}

//	@Disabled
	@RepeatedTest(10)
	@Tariff(3.0)
	public void parallelRegistration() {

		PMO_PhoneInterfaceImplementation pi = new PMO_PhoneInterfaceImplementation();
		PMO_Barrier barrier = new PMO_Barrier(PMO_Consts.PARALLEL_REGISTRATION_THREADS, true, true, true,
				"Parallel registration");

		List<PMO_PhoneRegistration> phones = generatePhones(PMO_Consts.PARALLEL_REGISTRATION_THREADS, pi);

		List<Runnable> tasks = phones.stream().map(ph -> PMO_ThreadsHelper.preBarrierWrapper(ph, barrier))
				.collect(Collectors.toList());

		PMO_ThreadsHelper.createAndStartThreads(tasks, true);

		barrier.trigger(true);

		PMO_TimeHelper.sleep(PMO_Consts.PARALLEL_TEST_TIME);

		phones.forEach(e -> e.test());
		phones.forEach(e -> testStateAfterRegistration(e.getNumber()));

		assertFalse(pi.connectionClosedExecuted(), "Metoda connectionClosed nie powinna zostac wykonana");
		assertFalse(pi.newConnectionExecuted(), "Metoda newConnectionExecuted nie powinna zostac wykonana");

	}

	@RepeatedTest(10)
	@Tariff(3.0)
//	@Disabled
	public void parallelPurchase() {
		PMO_PhoneInterfaceImplementation pi = new PMO_PhoneInterfaceImplementation();

		PMO_Barrier registrationBarrier = new PMO_Barrier(PMO_Consts.PARALLEL_PURCHASE_PHONES, true, true, true,
				"Parallel purchase registration");

		List<PMO_PhoneRegistration> phones = generatePhones(PMO_Consts.PARALLEL_PURCHASE_PHONES, pi);
		List<PMO_Purchase> purchases = new ArrayList<PMO_Purchase>();
		Map<String, Long> purchasedTime = new HashMap<String, Long>();

		String number;
		long sum;
		long randomValue;
		Random rnd = new Random();
		for (PMO_PhoneRegistration phone : phones) {
			number = phone.getNumber();
			sum = 0;

			for (int i = 0; i < PMO_Consts.PARALLEL_PURCHASE_THREADS_PER_PHONE; i++) {
				randomValue = PMO_Consts.PARALLEL_PURCHASES_IN_ONE_THREAD * (10 + rnd.nextInt(1000));
				sum += randomValue;
				purchases.add(new PMO_Purchase(asi, number, randomValue));
			}

			purchasedTime.put(number, sum);
		}

		List<Runnable> registrationTasks = phones.stream()
				.map(ph -> PMO_ThreadsHelper.preBarrierWrapper(ph, registrationBarrier)).collect(Collectors.toList());

		PMO_Barrier purchaseBarrier = new PMO_Barrier(
				PMO_Consts.PARALLEL_PURCHASE_THREADS_PER_PHONE * PMO_Consts.PARALLEL_PURCHASE_PHONES, true, true, true,
				"Purchase");
		List<Runnable> purchaseTasks = purchases.stream()
				.map(ph -> PMO_ThreadsHelper.preBarrierWrapper(ph, purchaseBarrier)).collect(Collectors.toList());

		PMO_ThreadsHelper.createAndStartThreads(registrationTasks, true);

		registrationBarrier.trigger(true);

		PMO_TimeHelper.sleep(PMO_Consts.PARALLEL_TEST_TIME);

		phones.forEach(e -> e.test());

		PMO_ThreadsHelper.createAndStartThreads(purchaseTasks, true);

		purchaseBarrier.trigger(true);

		PMO_TimeHelper.sleep(PMO_Consts.PARALLEL_PURCHASE_TIME);

		purchases.forEach(e -> e.test());

		// test wartosci wykupionych abonamentow

		long actual;
		for (Map.Entry<String, Long> entry : purchasedTime.entrySet()) {
			long expectedTime = entry.getValue();
			String numberE = entry.getKey();
			Optional<Long> actualO = PMO_TestHelper.tryToExecute(() -> asi.getRemainingTime(numberE),
					"getRemainingTime", 100);

			assertNotNull(actualO, "Wynik metody getRemainingTime nigdy nie może być null");
			assertTrue(actualO.isPresent(),
					"Wynik getRemainingTime dla zarejestrowanego telefonu to pusty obiekt Optional");

			actual = actualO.get();
			assertEquals(expectedTime, actual, "Brak zgodności pomiędzy zakupami a zeznaniem metody getRemainingTime");
		}
	}

//	@Disabled
	@RepeatedTest(10)
	@Tariff(3.0)
	public void concurrentConnections() {
		List<String> numbers = Stream.generate(PMO_PhoneNumberGenerator::getNumber)
				.limit(PMO_Consts.CONCONNECTIONS_PHONES).collect(Collectors.toList());

		PMO_Barrier newConnectionBarrier = new PMO_Barrier(PMO_Consts.CONCONNECTIONS_PHONES, true, true, true,
				"newConnectionBarrier");
		Map<String, PMO_PhoneInterfaceImplementation> phoneInterfacesMap = generatePhoneInterfaceMap(numbers,
				newConnectionBarrier, false);

		List<PMO_PhoneRegistration> registrations = generatePhoneRegistration(phoneInterfacesMap);
		List<PMO_Purchase> purchases = generatePurchase(phoneInterfacesMap, 10000);

		executeWithTimeout(registrations, 750);
		executeWithTimeout(purchases, 750);

		// podzielić numery na 2 części dzwoniący/odbierający - przyda się w 2-giej
		// części

		List<String> sources = numbers.subList(0, PMO_Consts.CONCONNECTIONS_PHONES / 2);
		List<String> destinations = numbers.subList(PMO_Consts.CONCONNECTIONS_PHONES / 2,
				PMO_Consts.CONCONNECTIONS_PHONES);

		List<PMO_PhoneCall> calls = new ArrayList<>();
		for (int i = 0; i < sources.size(); i++) {
			calls.add(new PMO_PhoneCall(sources.get(i), destinations.get(i), 0, asi));
		}
		PMO_Barrier callBarrier = new PMO_Barrier(calls.size(), true, true, true, "callBarrier");
		List<Runnable> callTasks = calls.stream().map(c -> PMO_ThreadsHelper.preBarrierWrapper(c, callBarrier))
				.collect(Collectors.toList());

		PMO_ThreadsHelper.createAndStartThreads(callTasks, true);

		callBarrier.trigger(true);

		PMO_TimeHelper.sleep(PMO_Consts.CONCONNECTIONS_TIME);

		destinations.stream().map(number -> phoneInterfacesMap.get(number))
				.forEach(pi -> assertTrue(pi.newConnectionExecuted(),
						"Oczekiwano wykonania metody newConnection dla " + pi.getNumber()));
	}

	@RepeatedTest(10)
	@Tariff(3.0)
//	@Disabled
	public void concurrentBlockingConnections() {
		List<String> numbers = Stream.generate(PMO_PhoneNumberGenerator::getNumber)
				.limit(PMO_Consts.CONCONNECTIONS_PHONES).collect(Collectors.toList());

		PMO_Barrier newConnectionBarrier = new PMO_Barrier(PMO_Consts.CONCONNECTIONS_PHONES / 3, true, true, false,
				"newConnectionBarrier CBC");
		Map<String, PMO_PhoneInterfaceImplementation> phoneInterfacesMap = generatePhoneInterfaceMap(numbers,
				newConnectionBarrier, true);

		List<PMO_PhoneRegistration> registrations = generatePhoneRegistration(phoneInterfacesMap);
		List<PMO_Purchase> purchases = generatePurchase(phoneInterfacesMap, 10000);

		executeWithTimeout(registrations, 750);
		executeWithTimeout(purchases, 750);

		// podzielić numery na 3 części

		List<String> sources1 = numbers.subList(0, PMO_Consts.CONCONNECTIONS_PHONES / 3);
		List<String> sources2 = numbers.subList(PMO_Consts.CONCONNECTIONS_PHONES / 3,
				2 * PMO_Consts.CONCONNECTIONS_PHONES / 3);
		List<String> sources3 = numbers.subList(2 * PMO_Consts.CONCONNECTIONS_PHONES / 3,
				PMO_Consts.CONCONNECTIONS_PHONES);

		List<PMO_PhoneCall> calls = new ArrayList<>();
		for (int i = 0; i < sources1.size(); i++) {
			calls.add(new PMO_PhoneCall(sources1.get(i), sources2.get(i), 0, asi));
			calls.add(new PMO_PhoneCall(sources2.get(i), sources3.get(i), 0, asi));
			calls.add(new PMO_PhoneCall(sources3.get(i), sources1.get(i), 0, asi));
		}
		PMO_Barrier callBarrier = new PMO_Barrier(calls.size(), true, true, true, "callBarrier CBC");
		List<Runnable> callTasks = calls.stream().map(c -> PMO_ThreadsHelper.preBarrierWrapper(c, callBarrier))
				.collect(Collectors.toList());

		PMO_ThreadsHelper.createAndStartThreads(callTasks, true);

		callBarrier.trigger(true);

		PMO_TimeHelper.sleep(PMO_Consts.CONCONNECTIONS_TIME);

		int isConnectedNumbers = numbers.stream()
				.mapToInt(n -> (PMO_OptionalHelper.testAndGet(asi.isConnected(n), true) ? 1 : 0)).sum();

		int expetectedIsConnected = 2 * PMO_Consts.CONCONNECTIONS_PHONES / 3;
		if (isConnectedNumbers > expetectedIsConnected) {
			fail("Błędny stan - co najmniej jeden telefon uczestniczy w dwóch, różnych rozmowach. Oczekiwano "
					+ expetectedIsConnected + " jest " + isConnectedNumbers);
		}
		if (isConnectedNumbers < expetectedIsConnected) {
			fail("Uzyskano zbyt mało połączonych telefonów. Oczekiwano " + expetectedIsConnected + " jest "
					+ isConnectedNumbers);
		}
	}

	private int bool2int(boolean bool) {
		return bool ? 1 : 0;
	}

	private int bool2int(boolean bool1, boolean bool2) {
		return bool2int(bool1) + bool2int(bool2);
	}

	@RepeatedTest(10)
	@Tariff(3.0)
//	@Disabled
	// A->B i jednocześnie B->A
	public void concurrentBlockingConnectionsPairs() {
		List<String> numbers = Stream.generate(PMO_PhoneNumberGenerator::getNumber)
				.limit(PMO_Consts.CONCONNECTIONS_PHONES).collect(Collectors.toList());

		PMO_Barrier newConnectionBarrier = new PMO_Barrier(PMO_Consts.CONCONNECTIONS_PHONES / 2, true, true, false,
				"newConnectionBarrier CBC2");
		Map<String, PMO_PhoneInterfaceImplementation> phoneInterfacesMap = generatePhoneInterfaceMap(numbers,
				newConnectionBarrier, true);

		List<PMO_PhoneRegistration> registrations = generatePhoneRegistration(phoneInterfacesMap);
		List<PMO_Purchase> purchases = generatePurchase(phoneInterfacesMap, 10000);

		executeWithTimeout(registrations, 750);
		executeWithTimeout(purchases, 750);

		// podzielić numery na 2 części dzwoniący/odbierający - przyda się w 2-giej
		// części

		List<String> sources1 = numbers.subList(0, PMO_Consts.CONCONNECTIONS_PHONES / 2);
		List<String> sources2 = numbers.subList(PMO_Consts.CONCONNECTIONS_PHONES / 2, PMO_Consts.CONCONNECTIONS_PHONES);

		List<PMO_PhoneCall> calls = new ArrayList<>();
		for (int i = 0; i < sources1.size(); i++) {
			calls.add(new PMO_PhoneCall(sources1.get(i), sources2.get(i), 0, asi));
			calls.add(new PMO_PhoneCall(sources2.get(i), sources1.get(i), 0, asi));
		}
		PMO_Barrier callBarrier = new PMO_Barrier(calls.size(), true, true, true, "callBarrier CBC2");
		List<Runnable> callTasks = calls.stream().map(c -> PMO_ThreadsHelper.preBarrierWrapper(c, callBarrier))
				.collect(Collectors.toList());

		PMO_ThreadsHelper.createAndStartThreads(callTasks, true);

		callBarrier.trigger(true);

		PMO_TimeHelper.sleep(PMO_Consts.CONCONNECTIONS_TIME);

		int isConnectedNumbers = numbers.stream()
				.mapToInt(n -> (PMO_OptionalHelper.testAndGet(asi.isConnected(n), true) ? 1 : 0)).sum();

		int expetectedIsConnected = PMO_Consts.CONCONNECTIONS_PHONES;
		assertEquals(expetectedIsConnected, isConnectedNumbers,
				"Błąd - oczekiwano, że każdy z numerów będzie w użyciu");

		// test newConnection
		String t1, t2;
		for (int i = 0; i < sources1.size(); i++) {
			t1 = sources1.get(i);
			t2 = sources2.get(i);
			assertEquals(1,
					bool2int(phoneInterfacesMap.get(t1).newConnectionExecuted(),
							phoneInterfacesMap.get(t2).newConnectionExecuted()),
					"Z pary telefonów (" + t1 + "," + t2 + " jeden powinien raportować wykonanie metody newConnection");
		}

	}

	// Zestawiamy polaczenia w parze. I konczymy polaczenie. Czy się rozłączy?
	// Połączenia tylko A->B
	@RepeatedTest(10)
	@Tariff(3.0)
//	@Disabled
	public void concurrentConnectionsInPairsDisconnection() {
		List<String> numbers = Stream.generate(PMO_PhoneNumberGenerator::getNumber)
				.limit(PMO_Consts.CONCONNECTIONS_PHONES).collect(Collectors.toList());

		PMO_Barrier newConnectionBarrier = new PMO_Barrier(PMO_Consts.CONCONNECTIONS_PHONES / 2, true, true, false,
				"newConnectionBarrier CBC2D");
		Map<String, PMO_PhoneInterfaceImplementation> phoneInterfacesMap = generatePhoneInterfaceMap(numbers,
				newConnectionBarrier, true);

		final long PURCHASE = 10000;
		List<PMO_PhoneRegistration> registrations = generatePhoneRegistration(phoneInterfacesMap);
		List<PMO_Purchase> purchases = generatePurchase(phoneInterfacesMap, PURCHASE);

		executeWithTimeout(registrations, 750);
		executeWithTimeout(purchases, 750);

		// sprawdzić stan początkowy zakupów.
		Map<String, Long> number2remainingBefore = new TreeMap<>();

		List<String> sources = numbers.subList(0, PMO_Consts.CONCONNECTIONS_PHONES / 2);
		List<String> destinations = numbers.subList(PMO_Consts.CONCONNECTIONS_PHONES / 2,
				PMO_Consts.CONCONNECTIONS_PHONES);

		Optional<Long> value;
		Long valueL;

		// stan finansów sprzed połączeń
		for (String tmp : sources) {
			value = PMO_TestHelper.tryToExecute(() -> asi.getRemainingTime(tmp), "getRemainingTime", 100);
			valueL = PMO_OptionalHelper.testAndGet(value, true);
			assertEquals(PURCHASE, (long) valueL, "Nie zgadza się stan zakupów dla telefonu " + tmp);
			number2remainingBefore.put(tmp, valueL);
		}

		List<PMO_PhoneCall> calls = new ArrayList<>();
		// Dzwonimy A->B
		for (int i = 0; i < sources.size(); i++) {
			calls.add(new PMO_PhoneCall(sources.get(i), destinations.get(i), phoneInterfacesMap.get(sources.get(i)),
					phoneInterfacesMap.get(destinations.get(i)), PMO_Consts.CONCONNECTIONS_DURATION, asi)); // brak
																											// rozłączenia
		}
		PMO_Barrier callBarrier = new PMO_Barrier(calls.size(), true, true, true, "callBarrier CBC2");
		List<Runnable> callTasks = calls.stream().map(c -> PMO_ThreadsHelper.preBarrierWrapper(c, callBarrier))
				.collect(Collectors.toList());

		PMO_ThreadsHelper.createAndStartThreads(callTasks, true);

		callBarrier.trigger(true);

		PMO_TimeHelper.sleep(PMO_Consts.CONCONNECTIONS_TIME);

		int isConnectedNumbersBeforeDisconnection = numbers.stream()
				.mapToInt(n -> (PMO_OptionalHelper.testAndGet(asi.isConnected(n), true) ? 1 : 0)).sum();

		PMO_TimeHelper.sleep(PMO_Consts.CONCONNECTIONS_DURATION + PMO_Consts.CONCONNECTIONS_TIME);

		int isConnectedNumbersAfterDisconnection = numbers.stream()
				.mapToInt(n -> (PMO_OptionalHelper.testAndGet(asi.isConnected(n), true) ? 1 : 0)).sum();

		// test wykonania newConnection
		String t1, t2;
		for (int i = 0; i < sources.size(); i++) {
			t1 = sources.get(i);
			t2 = destinations.get(i);
			assertEquals(1,
					bool2int(phoneInterfacesMap.get(t1).newConnectionExecuted(),
							phoneInterfacesMap.get(t2).newConnectionExecuted()),
					"Z pary telefonów (" + t1 + "," + t2 + " jeden powinien raportować wykonanie metody newConnection");
		}

		assertEquals(PMO_Consts.CONCONNECTIONS_PHONES, isConnectedNumbersBeforeDisconnection,
				"Błąd - oczekiwano, że wszystkie telefony będą w użyciu");
		assertEquals(0, isConnectedNumbersAfterDisconnection,
				"Błąd - oczekiwano, że po disconnect żaden z telefonów nie będzie w użyciu");

		// stan finansów po zakończeniu połączeń
		long delta;
		for (String tmp : sources) {
			value = PMO_TestHelper.tryToExecute(() -> asi.getRemainingTime(tmp), "getRemainingTime", 100);
			valueL = PMO_OptionalHelper.testAndGet(value, true);

			if (valueL > PURCHASE) {
				fail("Konto użytkownika po rozmowie raportuje _wzrost_ ilości czasu");
			}

			delta = Math.abs(PURCHASE - valueL - PMO_Consts.CONCONNECTIONS_DURATION);
			if (delta > 500) {
				fail("Nie zgadza się stan zakupów dla telefonu " + tmp + ". Rozłączenie zgłoszono po "
						+ PMO_Consts.CONCONNECTIONS_DURATION + " a z konta znikło " + (PURCHASE - valueL));
			}
		}

		for (PMO_PhoneCall tmp : calls) {

			valueL = tmp.getDisconnectedAt() - tmp.getConnectionStartedAt();

			delta = Math.abs(valueL - PMO_Consts.CONCONNECTIONS_DURATION);
			if (delta > 500) {
				fail("Nie zgadza się czas trwania połączenia dla telefonu " + tmp + ". Rozłączenie zgłoszono po "
						+ PMO_Consts.CONCONNECTIONS_DURATION + " a czas połączenia to " + valueL);
			}
		}

		for (Map.Entry<String, PMO_PhoneInterfaceImplementation> phone : phoneInterfacesMap.entrySet()) {
			assertTrue(phone.getValue().connectionClosedExecuted(),
					"Dla telefonu " + phone.getKey() + " nie wykonano connectionClosed");
		}

	}

	// Zestawiamy polaczenia w parze. Sami nie konczymy polaczenia. Czy się
	// rozłączy?
	// A->B
	@RepeatedTest(10)
	@Tariff(3.0)
//	@Disabled
	public void concurrentConnectionsInPairsAutodisconnection() {

		try {

			List<String> numbers = Stream.generate(PMO_PhoneNumberGenerator::getNumber)
					.limit(PMO_Consts.CONCONNECTIONS_PHONES).collect(Collectors.toList());

			PMO_Barrier newConnectionBarrier = new PMO_Barrier(PMO_Consts.CONCONNECTIONS_PHONES / 2, true, true, false,
					"newConnectionBarrier Autodisconnection");
			Map<String, PMO_PhoneInterfaceImplementation> phoneInterfacesMap = generatePhoneInterfaceMap(numbers,
					newConnectionBarrier, true);

			final long PURCHASE = 4000;
			List<PMO_PhoneRegistration> registrations = generatePhoneRegistration(phoneInterfacesMap);
			List<PMO_Purchase> purchases = generatePurchase(phoneInterfacesMap, PURCHASE);

			executeWithTimeout(registrations, 750);
			executeWithTimeout(purchases, 750);

			// sprawdzić stan początkowy zakupów.
			Map<String, Long> number2remainingBefore = new TreeMap<>();

			List<String> sources = numbers.subList(0, PMO_Consts.CONCONNECTIONS_PHONES / 2);
			List<String> destinations = numbers.subList(PMO_Consts.CONCONNECTIONS_PHONES / 2,
					PMO_Consts.CONCONNECTIONS_PHONES);

			Optional<Long> value;
			Long valueL;

			// stan finansów sprzed połączeń
			for (String tmp : sources) {
				value = PMO_TestHelper.tryToExecute(() -> asi.getRemainingTime(tmp), "getRemainingTime", 100);
				valueL = PMO_OptionalHelper.testAndGet(value, true);
				assertEquals(PURCHASE, (long) valueL, "Nie zgadza się stan zakupów dla telefonu " + tmp);
				number2remainingBefore.put(tmp, valueL);
			}

			List<PMO_PhoneCall> calls = new ArrayList<>();
			// Dzwonimy A->B
			for (int i = 0; i < sources.size(); i++) {
				calls.add(new PMO_PhoneCall(sources.get(i), destinations.get(i), phoneInterfacesMap.get(sources.get(i)),
						phoneInterfacesMap.get(destinations.get(i)), 0, asi)); // brak rozłączenia
			}
			PMO_Barrier callBarrier = new PMO_Barrier(calls.size(), true, true, true, "callBarrier CBC2aD");
			List<Runnable> callTasks = calls.stream().map(c -> PMO_ThreadsHelper.preBarrierWrapper(c, callBarrier))
					.collect(Collectors.toList());

			PMO_ThreadsHelper.createAndStartThreads(callTasks, true);

			callBarrier.trigger(true);

			PMO_TimeHelper.sleep(PMO_Consts.CONCONNECTIONS_TIME);

			int isConnectedNumbersBeforeDisconnection = numbers.stream()
					.mapToInt(n -> (PMO_OptionalHelper.testAndGet(asi.isConnected(n), true) ? 1 : 0)).sum();

			PMO_TimeHelper.sleep(PURCHASE + PMO_Consts.CONCONNECTIONS_TIME); // w tym czasie automat powinien
			// rozłączyć połączenia

			int isConnectedNumbersAfterDisconnection = numbers.stream()
					.mapToInt(n -> (PMO_OptionalHelper.testAndGet(asi.isConnected(n), true) ? 1 : 0)).sum();

			// test wykonania newConnection
			String t1, t2;
			for (int i = 0; i < sources.size(); i++) {
				t1 = sources.get(i);
				t2 = destinations.get(i);
				assertEquals(1,
						bool2int(phoneInterfacesMap.get(t1).newConnectionExecuted(),
								phoneInterfacesMap.get(t2).newConnectionExecuted()),
						"Z pary telefonów (" + t1 + "," + t2
								+ " jeden powinien raportować wykonanie metody newConnection");
			}

			assertEquals(PMO_Consts.CONCONNECTIONS_PHONES, isConnectedNumbersBeforeDisconnection,
					"Błąd - oczekiwano, że wszystkie telefony będą w użyciu");
			assertEquals(0, isConnectedNumbersAfterDisconnection,
					"Błąd - oczekiwano, że po disconnect żaden z telefonów nie będzie w użyciu");

			// stan finansów po automatycznym zakończeniu połączeń
			long delta;
			if (PMO_Consts.AUTOMATIC_DISCONNECTION_REMAINIG_TIME_TEST_ENABLED) {
				for (String tmp : sources) {
					value = PMO_TestHelper.tryToExecute(() -> asi.getRemainingTime(tmp), "getRemainingTime", 100);
					valueL = PMO_OptionalHelper.testAndGet(value, true);

					if (Math.abs(valueL) > 0) {
						fail("Konto użytkownika po automatycznie zakończonej rozmowie raportuje getRemainingTime != 0. Jest "
								+ valueL);
					}
				}
			}

			for (PMO_PhoneCall tmp : calls) {

				valueL = tmp.getDisconnectedAt() - tmp.getConnectionStartedAt();

				delta = Math.abs(valueL - PURCHASE);
				if (delta > 500) {
					fail("Nie zgadza się rozliczenia czasu dla telefonu " + tmp + ". Zakupiono " + PURCHASE
							+ " a połączenia rozłączono po " + valueL);
				}
			}

			for (Map.Entry<String, PMO_PhoneInterfaceImplementation> phone : phoneInterfacesMap.entrySet()) {
				assertTrue(phone.getValue().connectionClosedExecuted(),
						"Dla telefonu " + phone.getKey() + " nie wykonano connectionClosed");
			}

		} catch (Exception e) {
			PMO_LogSource.errorS(PMO_InstanceHelper.stackTrace2String(e));
			fail("W trakcie testu doszło do wyjątku " + e.toString());
		}

	}

	// Kupujemy 6 sekund. Zestawiamy połączenie. Pierwsze polaczenie trwa
	// 2s. Potem 2s przerwy i ponowne polaczenie. Powinno móc trawać 4 sekundy.
	// Jeśli zostanie
	// przerwane po 2 to błąd!
	// Zestawiamy polaczenia w parze. Sami nie konczymy polaczenia. Czy się
	// rozłączy?
	// A->B
	@RepeatedTest(10)
	@Tariff(3.0)
	public void concurrentConnectionsInPairsAutodisconnection2() {

		try {

			List<String> numbers = Stream.generate(PMO_PhoneNumberGenerator::getNumber)
					.limit(PMO_Consts.CONCONNECTIONS_PHONES).collect(Collectors.toList());

			PMO_Barrier newConnectionBarrier = new PMO_Barrier(PMO_Consts.CONCONNECTIONS_PHONES / 2, true, true, false,
					"newConnectionBarrier");
			Map<String, PMO_PhoneInterfaceImplementation> phoneInterfacesMap = generatePhoneInterfaceMap(numbers,
					newConnectionBarrier, true);

			final long PURCHASE = 6000;
			final long DURATION = 2000;
			List<PMO_PhoneRegistration> registrations = generatePhoneRegistration(phoneInterfacesMap);
			List<PMO_Purchase> purchases = generatePurchase(phoneInterfacesMap, PURCHASE);

			executeWithTimeout(registrations, 750);
			executeWithTimeout(purchases, 750);

			List<String> sources = numbers.subList(0, PMO_Consts.CONCONNECTIONS_PHONES / 2);
			List<String> destinations = numbers.subList(PMO_Consts.CONCONNECTIONS_PHONES / 2,
					PMO_Consts.CONCONNECTIONS_PHONES);

			Long valueL;

			List<PMO_PhoneCall> calls = new ArrayList<>();
			List<PMO_PhoneCall> calls2 = new ArrayList<>();
			// Dzwonimy A->B
			for (int i = 0; i < sources.size(); i++) {
				calls.add(new PMO_PhoneCall(sources.get(i), destinations.get(i), phoneInterfacesMap.get(sources.get(i)),
						phoneInterfacesMap.get(destinations.get(i)), DURATION, asi)); // rozłączenie po 2s
				calls2.add(new PMO_PhoneCall(sources.get(i), destinations.get(i),
						phoneInterfacesMap.get(sources.get(i)), phoneInterfacesMap.get(destinations.get(i)), 0, asi)); // brak
																														// rozłączenia
			}
			PMO_Barrier callBarrier = new PMO_Barrier(calls.size(), true, true, true, "callBarrier CBC2aD");
			List<Runnable> callTasks = calls.stream().map(c -> PMO_ThreadsHelper.preBarrierWrapper(c, callBarrier))
					.collect(Collectors.toList());
			PMO_Barrier callBarrier2 = new PMO_Barrier(calls.size(), true, true, true, "callBarrier CBC2aD 2");
			List<Runnable> call2Tasks = calls2.stream().map(c -> PMO_ThreadsHelper.preBarrierWrapper(c, callBarrier2))
					.collect(Collectors.toList());

			PMO_LogSource.logS("------------- create & start threads --------------");
			PMO_ThreadsHelper.createAndStartThreads(callTasks, true);
			PMO_ThreadsHelper.createAndStartThreads(call2Tasks, true);

			PMO_LogSource.logS("------------- first trigger --------------");
			callBarrier.trigger(true); // uruchamiamy pierwsze połączenie

			PMO_TimeHelper.sleep(2 * DURATION); // czekamy na utworzenie i zamknięcie połączeń

			PMO_LogSource.logS("------------- second trigger --------------");
			callBarrier2.trigger(true); // uruchamiamy ponowne połączenie, ale to się samo nie rozłączy

			PMO_TimeHelper.sleep(PURCHASE); // w tym czasie automat na pewno powinien zamknąć połączenie

			PMO_LogSource.logS("------------- test start --------------");
			int isConnectedNumbersAfterDisconnection = numbers.stream()
					.mapToInt(n -> (PMO_OptionalHelper.testAndGet(asi.isConnected(n), true) ? 1 : 0)).sum();

			assertEquals(0, isConnectedNumbersAfterDisconnection,
					"Błąd - oczekiwano, że obecnie żaden z telefonów nie będzie w użyciu");

			// ile trwało drugie połączenie?
			long delta;
			long expected = PURCHASE - DURATION;
			String phones;
			for (PMO_PhoneCall tmp : calls2) {

				valueL = tmp.getDisconnectedAt() - tmp.getConnectionStartedAt();

				delta = Math.abs(valueL - expected);

				phones = tmp.getNumbers();

				if (delta > 500) {
					PMO_LogSource.logS("Badamy połączenie " + phones + " started@ " + tmp.getConnectionStartedAt()
							+ " finished@ " + tmp.getDisconnectedAt() + " duration: " + valueL);
					fail("Oczekiwano, że drugie połączenie " + phones + " będzie trwać około " + expected + " a było "
							+ valueL);
				}

			}
		} catch (Exception e) {
			PMO_LogSource.errorS(PMO_InstanceHelper.stackTrace2String(e));
			fail("W trakcie testu doszło do wyjątku " + e.toString());
		}

	}

	@AfterEach
	public void shutdown() {
	}

}
