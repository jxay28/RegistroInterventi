# Registro Interventi

App Android personale per registrare gli interventi di manutenzione sui macchinari e sincronizzarli con Google Sheets.

## Contenuto

- `android-app/`: progetto Android Java.
- `google-apps-script/`: script Google Apps Script e dashboard per Google Sites.
- `docs/`: guide di configurazione.
- `release/Registro-Interventi.apk`: APK installabile.

## Sicurezza

Prima di pubblicare Google Apps Script, imposta un valore personale per `SECRET_TOKEN`. Non caricare mai nel repository un token reale, una chiave di firma Android o configurazioni locali.

## Compilazione

Apri `android-app` con Android Studio e genera una build release. Per configurare la sincronizzazione, segui le guide in `docs/`.
