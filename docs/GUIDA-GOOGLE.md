# Collegare Registro Interventi a Google Sheets

L'operazione va fatta una sola volta e richiede circa cinque minuti.

1. Apri `https://script.google.com` con il tuo account Google.
2. Premi **Nuovo progetto**.
3. Seleziona tutto il contenuto di `Codice.gs` nell'editor, cancellalo completamente e incolla tutto il file `Code.gs` fornito insieme all'APK. Non incollarlo dentro la funzione `myFunction` esistente.
4. Nella prima riga dello script cambia `CAMBIA-QUESTO-CODICE-SEGRETO` con un codice lungo scelto da te. Non eliminare gli apici.
5. Apri **Impostazioni progetto**, imposta il fuso orario su **Roma** e torna all'editor.
6. Premi **Esegui** scegliendo la funzione `doGet` e autorizza il progetto. Un file chiamato **Registro Interventi** verrà creato nel tuo Google Drive.
7. In alto a destra premi **Esegui il deployment** → **Nuovo deployment**.
8. Seleziona il tipo **App web**. Imposta:
   - *Esegui come*: **Me**;
   - *Chi può accedere*: **Chiunque**.
9. Premi **Esegui il deployment** e copia l'indirizzo che termina con `/exec`.
10. Nell'app Android apri **Google Sheets**, incolla l'indirizzo e inserisci lo stesso codice segreto del punto 4.
11. Premi **Salva e sincronizza**.

Il foglio resta nel tuo Drive. L'indirizzo web è protetto dal codice segreto; non condividerli con altre persone.

## Se modifichi lo script in futuro

Dopo una modifica scegli **Esegui il deployment** → **Gestisci deployment** → matita → **Nuova versione** → **Esegui il deployment**. L'indirizzo `/exec` resterà invariato.

## Aggiornamento dalla versione 1.0

Per sincronizzare anche modifiche ed eliminazioni devi sostituire il vecchio contenuto di `Code.gs` con quello nuovo. Conserva lo stesso `SECRET_TOKEN`, poi scegli **Esegui il deployment** → **Gestisci deployment** → matita → **Nuova versione** → **Esegui il deployment**. Non serve cambiare l'indirizzo salvato nell'app.

## Ripristinare i dati su un nuovo telefono

1. Installa la versione 1.2 o successiva dell'APK.
2. Sostituisci il contenuto di `Codice.gs` con il file aggiornato, conservando lo stesso `SECRET_TOKEN`.
3. Pubblica una **Nuova versione** dello stesso deployment.
4. Nell'app apri **Google Sheets** e inserisci l'indirizzo `/exec` e il codice segreto.
5. Premi **Ripristina da Google Sheets** e conferma.

Il ripristino aggiunge soltanto i macchinari e gli interventi mancanti. Non cancella i dati già presenti sul telefono e usa gli identificativi della prima colonna per evitare duplicati.
