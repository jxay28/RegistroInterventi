# Dashboard manutenzioni in Google Sites

Il dashboard legge le schede `Interventi` e `Macchinari` senza aggiungere o modificare righe nel foglio. Mostra:

- totale interventi, tempo totale, macchinari coinvolti e durata media;
- grafico a torta del tempo impiegato per ciascun macchinario;
- grafico del numero di interventi per macchinario;
- filtro per macchinario e periodo;
- tabella dello storico degli interventi filtrato.

## 1. Aggiorna lo script

1. Apri il progetto Apps Script già collegato all'app.
2. Sostituisci il contenuto di `Codice.gs` con il file aggiornato fornito.
3. Crea un nuovo file nell'editor: **+** → **HTML**. Chiamalo esattamente `Dashboard`.
4. Incolla nel nuovo file tutto il contenuto di `Dashboard.html` fornito.
5. Mantieni invariato il tuo `SECRET_TOKEN`.
6. Salva entrambi i file.

## 2. Pubblica il dashboard

1. Seleziona **Esegui il deployment** → **Gestisci deployment**.
2. Apri il deployment esistente e premi la matita.
3. Seleziona **Nuova versione**, quindi **Esegui il deployment**.
4. L'indirizzo `/exec` resta uguale.

L'indirizzo da incorporare nel sito è:

`https://script.google.com/macros/s/IL_TUO_ID/exec?dashboard=1&token=IL_TUO_SECRET_TOKEN`

Sostituisci soltanto la parte dopo `/exec?dashboard=1&token=` con il tuo stesso codice segreto. Non condividere questo indirizzo.

## 3. Incorpora in Google Sites

1. Apri il tuo sito in Google Sites e crea una pagina, ad esempio `Riepilogo manutenzioni`.
2. A destra seleziona **Inserisci** → **Incorpora** → **Per URL**.
3. Incolla l'indirizzo del dashboard descritto sopra.
4. Allarga il riquadro a tutta la larghezza della pagina e imposta un'altezza di almeno 900 pixel.
5. Pubblica il sito. Per maggiore riservatezza, limita la visibilità del sito al tuo account Google.

Ogni volta che l'app sincronizza un nuovo intervento, il dashboard lo mostrerà premendo **Aggiorna dati** oppure riaprendo la pagina.
