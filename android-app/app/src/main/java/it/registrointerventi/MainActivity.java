package it.registrointerventi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int PRIMARY = Color.rgb(21, 94, 117);
    private static final int PRIMARY_DARK = Color.rgb(22, 78, 99);
    private static final int ACCENT = Color.rgb(245, 158, 11);
    private static final int TEXT = Color.rgb(241, 245, 249);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int SURFACE = Color.rgb(15, 23, 42);
    private static final int PANEL = Color.rgb(30, 41, 59);
    private static final int BORDER = Color.rgb(71, 85, 105);

    private final DateTimeFormatter displayDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY);
    private final DateTimeFormatter isoDate = DateTimeFormatter.ISO_LOCAL_DATE;
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALY);

    private DatabaseHelper db;
    private GoogleSync sync;
    private SharedPreferences prefs;
    private String currentScreen = "home";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
        sync = new GoogleSync(db);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        if (!prefs.contains("secret")) {
            prefs.edit().putString("secret", UUID.randomUUID().toString()).apply();
        }
        showHome();
    }

    @Override
    public void onBackPressed() {
        if (!"home".equals(currentScreen)) showHome();
        else super.onBackPressed();
    }

    private void showHome() {
        currentScreen = "home";
        LinearLayout content = screen("Registro Interventi", false);

        TextView intro = text("Il tuo diario di manutenzione", 22, TEXT, true);
        intro.setPadding(0, dp(8), 0, dp(4));
        content.addView(intro);
        content.addView(text("Registra il lavoro, ritrova i macchinari e salva tutto nel tuo Google Drive.", 16, MUTED, false));
        space(content, 20);

        Button newWork = actionButton("＋  NUOVO INTERVENTO", ACCENT, Color.rgb(20, 30, 40));
        newWork.setOnClickListener(v -> showNewIntervention());
        content.addView(newWork);
        space(content, 12);

        Button machines = actionButton("⚙  MACCHINARI", PRIMARY, Color.WHITE);
        machines.setOnClickListener(v -> showMachines());
        content.addView(machines);
        space(content, 12);

        Button history = actionButton("☷  STORICO INTERVENTI", PRIMARY, Color.WHITE);
        history.setOnClickListener(v -> showHistory());
        content.addView(history);
        space(content, 12);

        Button settings = actionButton("☁  GOOGLE SHEETS", PRIMARY_DARK, Color.WHITE);
        settings.setOnClickListener(v -> showSettings());
        content.addView(settings);
        space(content, 24);

        int pending = db.pendingCount();
        String endpoint = prefs.getString("endpoint", "");
        TextView status;
        if (endpoint.isEmpty()) {
            status = statusBox("Google Sheets non è ancora collegato. I dati vengono comunque salvati sul telefono.", false);
        } else if (pending > 0) {
            status = statusBox(pending + " elementi in attesa di sincronizzazione.", false);
            status.setOnClickListener(v -> runSync(status));
        } else {
            status = statusBox("✓ Dati sincronizzati con Google Sheets", true);
        }
        content.addView(status);
    }

    private void showMachines() {
        currentScreen = "machines";
        LinearLayout content = screen("Macchinari", true);
        content.addView(text("Aggiungi un macchinario una sola volta: sarà disponibile per tutti gli interventi futuri.", 15, MUTED, false));
        space(content, 12);

        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText name = input("Nome macchinario", false);
        addRow.addView(name, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button add = compactButton("AGGIUNGI");
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(108), dp(54));
        addParams.setMargins(dp(8), 0, 0, 0);
        addRow.addView(add, addParams);
        content.addView(addRow);
        space(content, 18);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        renderMachineList(list);

        add.setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            if (value.isEmpty()) {
                name.setError("Inserisci il nome");
                return;
            }
            DatabaseHelper.Machine existing = db.findMachineByName(value);
            if (existing != null) {
                Toast.makeText(this, "Macchinario già presente", Toast.LENGTH_SHORT).show();
                return;
            }
            DatabaseHelper.Machine machine = db.addMachine(value, OffsetDateTime.now().toString());
            if (machine != null) {
                name.setText("");
                hideKeyboard(name);
                renderMachineList(list);
                syncIfConfigured();
            }
        });
    }

    private void renderMachineList(LinearLayout list) {
        list.removeAllViews();
        List<DatabaseHelper.Machine> machines = db.getMachines();
        if (machines.isEmpty()) {
            list.addView(statusBox("Nessun macchinario salvato.", false));
            return;
        }
        for (DatabaseHelper.Machine machine : machines) {
            LinearLayout row = card();
            TextView label = text(machine.name, 18, TEXT, true);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView state = text(machine.synced ? "☁" : "○", 18, machine.synced ? PRIMARY : MUTED, true);
            state.setGravity(Gravity.CENTER);
            row.addView(state, new LinearLayout.LayoutParams(dp(42), dp(42)));
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Eliminare il macchinario?")
                        .setMessage("Puoi eliminarlo solo se non ha interventi registrati.")
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Elimina", (d, w) -> {
                            if (!db.deleteMachine(machine.id)) {
                                Toast.makeText(this, "Macchinario usato nello storico: non può essere eliminato", Toast.LENGTH_LONG).show();
                            }
                            renderMachineList(list);
                        }).show();
                return true;
            });
            list.addView(row);
            space(list, 8);
        }
        TextView hint = text("Tieni premuto un macchinario per eliminarlo.", 13, MUTED, false);
        hint.setPadding(0, dp(6), 0, 0);
        list.addView(hint);
    }

    private void showNewIntervention() {
        showInterventionForm(null);
    }

    private void showInterventionForm(DatabaseHelper.Intervention existing) {
        List<DatabaseHelper.Machine> machines = db.getMachines();
        if (machines.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Prima aggiungi un macchinario")
                    .setMessage("La libreria è vuota. Inserisci il primo macchinario e poi registra l'intervento.")
                    .setPositiveButton("Vai ai macchinari", (d, w) -> showMachines())
                    .setNegativeButton("Annulla", null).show();
            return;
        }

        boolean editing = existing != null;
        currentScreen = editing ? "edit" : "new";
        LinearLayout content = screen(editing ? "Modifica intervento" : "Nuovo intervento", true);

        content.addView(fieldLabel("Macchinario"));
        Spinner machineSpinner = new Spinner(this);
        ArrayAdapter<DatabaseHelper.Machine> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, machines);
        machineSpinner.setAdapter(adapter);
        machineSpinner.setPadding(dp(10), 0, dp(10), 0);
        machineSpinner.setBackground(rounded(PANEL, BORDER, 8));
        content.addView(machineSpinner, matchHeight(56));
        if (editing) {
            for (int i = 0; i < machines.size(); i++) {
                if (machines.get(i).id.equals(existing.machineId)) {
                    machineSpinner.setSelection(i);
                    break;
                }
            }
        }
        space(content, 14);

        LocalDate[] selectedDate = {editing ? LocalDate.parse(existing.workDate, isoDate) : LocalDate.now()};
        LocalTime[] start = {editing ? LocalTime.parse(existing.startTime, timeFormat) :
                LocalTime.now().withSecond(0).withNano(0)};
        LocalTime[] end = {editing ? LocalTime.parse(existing.endTime, timeFormat) :
                LocalTime.now().withSecond(0).withNano(0)};

        content.addView(fieldLabel("Data"));
        Button dateButton = selectorButton(selectedDate[0].format(displayDate));
        content.addView(dateButton, matchHeight(54));
        dateButton.setOnClickListener(v -> new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate[0] = LocalDate.of(year, month + 1, day);
            dateButton.setText(selectedDate[0].format(displayDate));
        }, selectedDate[0].getYear(), selectedDate[0].getMonthValue() - 1, selectedDate[0].getDayOfMonth()).show());
        space(content, 14);

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout startCol = column();
        LinearLayout endCol = column();
        startCol.addView(fieldLabel("Ora inizio"));
        endCol.addView(fieldLabel("Ora fine"));
        Button startButton = selectorButton(start[0].format(timeFormat));
        Button endButton = selectorButton(end[0].format(timeFormat));
        startCol.addView(startButton, matchHeight(54));
        endCol.addView(endButton, matchHeight(54));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams halfRight = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        halfRight.setMargins(dp(10), 0, 0, 0);
        times.addView(startCol, half);
        times.addView(endCol, halfRight);
        content.addView(times);
        startButton.setOnClickListener(v -> pickTime(start[0], chosen -> {
            start[0] = chosen;
            startButton.setText(chosen.format(timeFormat));
        }));
        endButton.setOnClickListener(v -> pickTime(end[0], chosen -> {
            end[0] = chosen;
            endButton.setText(chosen.format(timeFormat));
        }));
        space(content, 14);

        content.addView(fieldLabel("Lavoro eseguito *"));
        EditText description = input("Descrivi l'intervento", true);
        if (editing) description.setText(existing.description);
        content.addView(description, matchHeight(100));
        space(content, 14);

        content.addView(fieldLabel("Note (facoltative)"));
        EditText notes = input("Ricambi, problemi trovati, promemoria…", true);
        if (editing) notes.setText(existing.notes);
        content.addView(notes, matchHeight(90));
        space(content, 20);

        Button save = actionButton(editing ? "SALVA MODIFICHE" : "SALVA INTERVENTO", ACCENT, Color.rgb(20, 30, 40));
        content.addView(save);
        save.setOnClickListener(v -> {
            String desc = description.getText().toString().trim();
            if (desc.isEmpty()) {
                description.setError("Descrivi il lavoro eseguito");
                description.requestFocus();
                return;
            }
            DatabaseHelper.Machine machine = (DatabaseHelper.Machine) machineSpinner.getSelectedItem();
            int duration = durationMinutes(selectedDate[0], start[0], end[0]);
            boolean success;
            if (editing) {
                success = db.updateIntervention(existing.id, machine.id, machine.name,
                        selectedDate[0].format(isoDate), start[0].format(timeFormat),
                        end[0].format(timeFormat), duration, desc,
                        notes.getText().toString().trim());
            } else {
                success = db.addIntervention(machine.id, machine.name,
                        selectedDate[0].format(isoDate), start[0].format(timeFormat),
                        end[0].format(timeFormat), duration, desc,
                        notes.getText().toString().trim(), OffsetDateTime.now().toString()) != null;
            }
            if (!success) {
                Toast.makeText(this, "Errore durante il salvataggio", Toast.LENGTH_LONG).show();
                return;
            }
            hideKeyboard(description);
            Toast.makeText(this, editing ? "Modifiche salvate" : "Intervento salvato", Toast.LENGTH_SHORT).show();
            syncIfConfigured();
            showHistory();
        });
    }

    private void showHistory() {
        currentScreen = "history";
        LinearLayout content = screen("Storico interventi", true);
        List<DatabaseHelper.Intervention> history = db.getHistory();
        if (history.isEmpty()) {
            content.addView(statusBox("Non ci sono ancora interventi registrati.", false));
            return;
        }

        TextView summary = text(history.size() + (history.size() == 1 ? " intervento registrato" : " interventi registrati"), 15, MUTED, false);
        content.addView(summary);
        space(content, 12);

        for (DatabaseHelper.Intervention item : history) {
            LinearLayout box = column();
            box.setPadding(dp(16), dp(14), dp(16), dp(14));
            box.setBackground(rounded(PANEL, BORDER, 12));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.addView(text(item.machineName, 18, Color.rgb(103, 232, 249), true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            top.addView(text(item.synced ? "☁" : "○", 17, item.synced ? PRIMARY : MUTED, true));
            box.addView(top);
            LocalDate date = LocalDate.parse(item.workDate, isoDate);
            box.addView(text(date.format(displayDate) + "  •  " + item.startTime + "–" + item.endTime +
                    "  •  " + formatDuration(item.durationMinutes), 14, MUTED, false));
            TextView desc = text(item.description, 16, TEXT, false);
            desc.setPadding(0, dp(10), 0, 0);
            box.addView(desc);
            if (!item.notes.isEmpty()) {
                TextView note = text("Note: " + item.notes, 14, MUTED, false);
                note.setPadding(0, dp(6), 0, 0);
                box.addView(note);
            }
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(12), 0, 0);
            Button edit = compactButton("MODIFICA");
            Button delete = actionButton("ELIMINA", Color.rgb(153, 27, 27), Color.WHITE);
            edit.setOnClickListener(v -> showInterventionForm(item));
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Eliminare l'intervento?")
                    .setMessage("Verrà eliminato dal telefono e, alla sincronizzazione, anche da Google Sheets.")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Elimina", (dialog, which) -> {
                        if (db.requestDeleteIntervention(item)) {
                            Toast.makeText(this, "Intervento eliminato", Toast.LENGTH_SHORT).show();
                            syncIfConfigured();
                            showHistory();
                        } else {
                            Toast.makeText(this, "Eliminazione non riuscita", Toast.LENGTH_LONG).show();
                        }
                    }).show());
            LinearLayout.LayoutParams actionHalf = new LinearLayout.LayoutParams(0, dp(48), 1);
            LinearLayout.LayoutParams actionRight = new LinearLayout.LayoutParams(0, dp(48), 1);
            actionRight.setMargins(dp(8), 0, 0, 0);
            actions.addView(edit, actionHalf);
            actions.addView(delete, actionRight);
            box.addView(actions);
            content.addView(box);
            space(content, 10);
        }

        if (db.pendingCount() > 0 && !prefs.getString("endpoint", "").isEmpty()) {
            Button syncButton = actionButton("SINCRONIZZA ORA", PRIMARY, Color.WHITE);
            syncButton.setOnClickListener(v -> runSync(syncButton));
            content.addView(syncButton);
        }
    }

    private void showSettings() {
        currentScreen = "settings";
        LinearLayout content = screen("Google Sheets", true);
        content.addView(text("Collega il foglio creato nel tuo Google Drive. Copia qui l'indirizzo dell'app web ottenuto da Google Apps Script.", 15, MUTED, false));
        space(content, 16);

        content.addView(fieldLabel("Indirizzo dello script"));
        EditText endpoint = input("https://script.google.com/macros/s/…/exec", true);
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setText(prefs.getString("endpoint", ""));
        content.addView(endpoint, matchHeight(100));
        space(content, 14);

        content.addView(fieldLabel("Codice segreto"));
        EditText secret = input("Codice di sicurezza", false);
        secret.setText(prefs.getString("secret", ""));
        content.addView(secret, matchHeight(54));
        TextView secretHint = text("Deve essere uguale al codice SECRET_TOKEN inserito nello script Google.", 13, MUTED, false);
        secretHint.setPadding(0, dp(5), 0, 0);
        content.addView(secretHint);
        space(content, 18);

        Button save = actionButton("SALVA E SINCRONIZZA", ACCENT, Color.rgb(20, 30, 40));
        content.addView(save);
        save.setOnClickListener(v -> {
            String url = endpoint.getText().toString().trim();
            String token = secret.getText().toString().trim();
            if (!url.startsWith("https://script.google.com/macros/s/") || !url.endsWith("/exec")) {
                endpoint.setError("Inserisci l'indirizzo /exec fornito da Google Apps Script");
                return;
            }
            if (token.isEmpty()) {
                secret.setError("Inserisci il codice segreto");
                return;
            }
            prefs.edit().putString("endpoint", url).putString("secret", token).apply();
            hideKeyboard(endpoint);
            runSync(save);
        });
        space(content, 14);
        Button restore = actionButton("RIPRISTINA DA GOOGLE SHEETS", PRIMARY, Color.WHITE);
        content.addView(restore);
        restore.setOnClickListener(v -> {
            String url = endpoint.getText().toString().trim();
            String token = secret.getText().toString().trim();
            if (!url.startsWith("https://script.google.com/macros/s/") || !url.endsWith("/exec")) {
                endpoint.setError("Inserisci l'indirizzo /exec fornito da Google Apps Script");
                return;
            }
            if (token.isEmpty()) {
                secret.setError("Inserisci il codice segreto");
                return;
            }
            prefs.edit().putString("endpoint", url).putString("secret", token).apply();
            hideKeyboard(endpoint);
            new AlertDialog.Builder(this)
                    .setTitle("Ripristinare lo storico?")
                    .setMessage("Verranno importati dal foglio Google i macchinari e gli interventi mancanti. I dati già presenti sul telefono non saranno cancellati.")
                    .setNegativeButton("Annulla", null)
                    .setPositiveButton("Ripristina", (dialog, which) -> runRestore(restore))
                    .show();
        });
        space(content, 14);
        content.addView(statusBox("I dati sono sempre salvati prima sul telefono. Se manca Internet, resteranno in attesa e potrai sincronizzarli più tardi.", true));
    }

    private void runSync(View source) {
        source.setEnabled(false);
        Toast.makeText(this, "Sincronizzazione in corso…", Toast.LENGTH_SHORT).show();
        sync.syncAll(prefs.getString("endpoint", ""), prefs.getString("secret", ""), (success, message) -> {
            source.setEnabled(true);
            new AlertDialog.Builder(this)
                    .setTitle(success ? "Google Sheets aggiornato" : "Sincronizzazione sospesa")
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> {
                        if (success) showHome();
                    }).show();
        });
    }

    private void runRestore(View source) {
        source.setEnabled(false);
        Toast.makeText(this, "Ripristino dal cloud in corso…", Toast.LENGTH_SHORT).show();
        sync.restoreAll(prefs.getString("endpoint", ""), prefs.getString("secret", ""), (success, message) -> {
            source.setEnabled(true);
            new AlertDialog.Builder(this)
                    .setTitle(success ? "Archivio ripristinato" : "Ripristino non riuscito")
                    .setMessage(message)
                    .setPositiveButton("OK", (d, w) -> {
                        if (success) showHistory();
                    }).show();
        });
    }

    private void syncIfConfigured() {
        String endpoint = prefs.getString("endpoint", "");
        if (!endpoint.isEmpty()) {
            sync.syncAll(endpoint, prefs.getString("secret", ""), (success, message) -> {
                if (!success) Toast.makeText(this, "Salvato sul telefono; sincronizzazione in attesa", Toast.LENGTH_LONG).show();
            });
        }
    }

    private LinearLayout screen(String title, boolean back) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(SURFACE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), 0, dp(16), 0);
        toolbar.setBackgroundColor(PRIMARY_DARK);
        if (back) {
            Button backButton = new Button(this);
            backButton.setText("‹");
            backButton.setTextSize(32);
            backButton.setTextColor(Color.WHITE);
            backButton.setBackgroundColor(Color.TRANSPARENT);
            backButton.setOnClickListener(v -> showHome());
            toolbar.addView(backButton, new LinearLayout.LayoutParams(dp(52), dp(60)));
        }
        TextView heading = text(title, 21, Color.WHITE, true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, dp(60), 1));
        page.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);
        return content;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(10), dp(10));
        row.setBackground(rounded(PANEL, BORDER, 10));
        row.setMinimumHeight(dp(62));
        return row;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView fieldLabel(String value) {
        TextView label = text(value, 14, TEXT, true);
        label.setPadding(0, 0, 0, dp(6));
        return label;
    }

    private EditText input(String hint, boolean multiline) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(16);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(Color.rgb(100, 116, 139));
        edit.setPadding(dp(13), dp(9), dp(13), dp(9));
        edit.setBackground(rounded(PANEL, BORDER, 8));
        edit.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        edit.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return edit;
    }

    private Button actionButton(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(rounded(background, background, 10));
        button.setMinHeight(dp(58));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        return button;
    }

    private Button compactButton(String label) {
        Button button = actionButton(label, PRIMARY, Color.WHITE);
        button.setTextSize(13);
        return button;
    }

    private Button selectorButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(14), 0, dp(10), 0);
        button.setBackground(rounded(PANEL, BORDER, 8));
        return button;
    }

    private TextView statusBox(String message, boolean good) {
        TextView box = text(message, 14, good ? Color.rgb(167, 243, 208) : MUTED, false);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(rounded(good ? Color.rgb(6, 78, 59) : PANEL,
                good ? Color.rgb(16, 185, 129) : BORDER, 9));
        return box;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private LinearLayout.LayoutParams matchHeight(int heightDp) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp));
    }

    private void space(LinearLayout parent, int heightDp) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void pickTime(LocalTime initial, TimeConsumer consumer) {
        new TimePickerDialog(this, (view, hour, minute) -> consumer.accept(LocalTime.of(hour, minute)),
                initial.getHour(), initial.getMinute(), true).show();
    }

    private int durationMinutes(LocalDate date, LocalTime start, LocalTime end) {
        LocalDateTime from = LocalDateTime.of(date, start);
        LocalDateTime to = LocalDateTime.of(date, end);
        if (to.isBefore(from)) to = to.plusDays(1);
        return (int) Duration.between(from, to).toMinutes();
    }

    private String formatDuration(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours == 0) return mins + " min";
        return mins == 0 ? hours + " h" : hours + " h " + mins + " min";
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private interface TimeConsumer { void accept(LocalTime time); }
}
