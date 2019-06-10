/**
 * Bardzo prosty interfejs pozwalający na przekazanie informacji z systemu do
 * telefonu
 */
public interface PhoneInterface {
    /**
     * Informacja o próbie nawiązania połączenia z numeru number. Metoda kończy
     * pracę, gdy użytkownik telefonu zdecyduje co zrobić - odebrać czy odrzucić
     * połączenie. Nie ma gwarancji, że stanie się to natychmiast.
     * 
     * @param number numer, z którego nawiązywane jest połączenie
     * @return true - połączenie zostało zaakceptowane, od tego momentu połączenie
     *         jest uznawane za zestawione i rozliczny jest czas połączenia. false -
     *         odrzucono połączenie.
     */
    public boolean newConnection(String number);

    /**
     * Metoda przekazuje informację o zakończeniu połączenia z numerem telefonu
     * number.
     * 
     * @param number numer telefonu, z którym prowadzona była właśnie zakończona rozmowa.
     */
    public void connectionClosed(String number);
}