import java.util.Optional;

public interface AccountingSystemInterface {

    /**
     * Metoda rejestruje telefon w systemie. Każdy telefon musi być zarejestrowany
     * przed użyciem.
     * 
     * @param number unikalny numer rejestrowanego telefonu
     * @param phone  interfejs pozwalający na komunikację z telefonem
     */
    public void phoneRegistration(String number, PhoneInterface phone);

    /**
     * Wykupienie abonamentu dla podanego numeru telefonu na określony czas
     * połączenia. Czas połączenia podawany jest milisekundach.
     * 
     * @param number numer telefonu, którego transakcja dotyczy
     * @param time   wykupiony czas połączenia w msec
     * @return sumaryczny, pozostały do dyspozycji czas połączenia dostępny dla
     *         użytkownika telefonu o numerze number. Wynik podawany jest w msec i
     *         uwzględnia poprzednie zakupy i połączenia (w tym aktualnego o ile
     *         takie istnieje).
     */
    public long subscriptionPurchase(String number, long time);

    /**
     * Metoda zwraca pozostały do użycia czas połączeń z numeru number. Wynik
     * podawany jest w msec.
     * 
     * @param number numer telefonu, dla którego zwracany jest czas pozostający
     *          do dyspozycji użytkownika.
     * @return jeśli nie zarejestrowano podanego numeru telefonu metoda zwraca pusty
     *         obiekt Optional. Jeśli numer jest zarejestrowany to wykonane metoda
     *         zwraca pozostały do użycia czas. Wynik musi uwzględniać zarówno
     *         wszystkie zakupy jak i zakończone połączenia, w tym połączenie
     *         aktualne (jeśli istnieje).
     */
    public Optional<Long> getRemainingTime(String number);

    /**
     * Metoda pozwala zgłosić rozpoczęcie połączenie pomiędzy numerem numberFrom
     * oraz numberTo. Połączenie jest rozpoczynane jeśli oba numery są
     * zarejestrowane, użytkownik numeru numberFrom posiada większą od 0 liczbę
     * msec, których można użyć do prowadzenia rozmów oraz numer numberTo nie jest
     * użyty w innym połączeniu.
     * 
     * @param numberFrom numer, który nawiązuje połączenie
     * @param numberTo   numer, do którego nawiązywane jest połączenie.
     * @return true jeśli połączenie udało się nawiązać, false - w przeciwnym
     *         przypadku
     */
    public boolean connection(String numberFrom, String numberTo);

    /**
     * Rozłączenie połączenia. Połączenie może rozłączyć dowolny z telefonów z
     * połączonej pary. Rozłączenie połączenia kończy pomiar czasu - z chwilą
     * zakończenia metody disconnection, metoda getBilling musi uwzględniać również
     * czas właśnie zakończonego połączenia.
     * @param number numer telefonu, który zleca rozłączenie połączenia
     */
    public void disconnection(String number);

    /**
     * Metoda zwraca (o ile istnieje) sumaryczny czas zakończonych połączeń z
     * telefonu numberFrom do telefonu o numerze numberTo.
     * 
     * @param numberFrom numer, dla którego czas połączeń jest zwracany
     * @param numberTo   numer, do którego były wykonywane połączenie telefoniczne
     * @return W przypadku gdy numer numberFrom i/lub numberTo jest nieznany
     *         zwracany jest obiekt Optional nie zawierający danych (pusty). W innym
     *         przypadku zwracany jest sumaryczny czas połączeń.
     */
    public Optional<Long> getBilling(String numberFrom, String numberTo);

    /**
     * Metoda zwraca informację o stanie połączenia do/z danego numeru telefonu.
     * Jeśli numer nie jest znany systemowi zwracany jest pusty obiekt Optional.
     * Jeśli numer jest zajęty zwracany jest obiekt Optional zawierający true. Jeśli
     * numer nie jest zajęty połączeniem zwracamy jest obiekt Optional zawierający
     * false.
     * 
     * @param number numer telefonu
     * @return jeśli numer jest znany to stan połączenia z/do tego numeru. Jeśli
     *         number nie jest systemowi znany, to zwracany jest pusty obiekt
     *         Optional.
     */
    public Optional<Boolean> isConnected(String number);
}