package it.registrointerventi;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoogleSync {
    private final DatabaseHelper db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GoogleSync(DatabaseHelper db) {
        this.db = db;
    }

    public interface Callback {
        void onComplete(boolean success, String message);
    }

    public void syncAll(String endpoint, String secret, Callback callback) {
        if (endpoint == null || !endpoint.startsWith("https://script.google.com/macros/s/")) {
            callback.onComplete(false, "Inserisci l'indirizzo dello script Google nelle Impostazioni.");
            return;
        }
        executor.execute(() -> {
            int sent = 0;
            try {
                List<DatabaseHelper.Machine> machines = db.getPendingMachines();
                for (DatabaseHelper.Machine machine : machines) {
                    JSONObject json = new JSONObject();
                    json.put("type", "machine");
                    json.put("token", secret == null ? "" : secret);
                    json.put("id", machine.id);
                    json.put("name", machine.name);
                    json.put("createdAt", machine.createdAt);
                    post(endpoint, json);
                    db.markMachineSynced(machine.id);
                    sent++;
                }

                List<DatabaseHelper.Intervention> interventions = db.getPendingInterventions();
                for (DatabaseHelper.Intervention item : interventions) {
                    JSONObject json = new JSONObject();
                    json.put("type", item.deleted ? "delete_intervention" : "intervention");
                    json.put("token", secret == null ? "" : secret);
                    json.put("id", item.id);
                    if (!item.deleted) {
                        json.put("machineId", item.machineId);
                        json.put("machineName", item.machineName);
                        json.put("date", item.workDate);
                        json.put("startTime", item.startTime);
                        json.put("endTime", item.endTime);
                        json.put("durationMinutes", item.durationMinutes);
                        json.put("description", item.description);
                        json.put("notes", item.notes);
                        json.put("createdAt", item.createdAt);
                    }
                    post(endpoint, json);
                    if (item.deleted) db.hardDeleteIntervention(item.id);
                    else db.markInterventionSynced(item.id);
                    sent++;
                }
                int finalSent = sent;
                mainHandler.post(() -> callback.onComplete(true,
                        finalSent == 0 ? "Tutto è già sincronizzato." :
                                "Sincronizzazione completata: " + finalSent + " elementi."));
            } catch (Exception ex) {
                String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                mainHandler.post(() -> callback.onComplete(false,
                        "Sincronizzazione non riuscita. I dati restano salvati sul telefono.\n" + detail));
            }
        });
    }

    public void restoreAll(String endpoint, String secret, Callback callback) {
        if (endpoint == null || !endpoint.startsWith("https://script.google.com/macros/s/")) {
            callback.onComplete(false, "Inserisci l'indirizzo dello script Google nelle Impostazioni.");
            return;
        }
        executor.execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("type", "restore");
                request.put("token", secret == null ? "" : secret);
                JSONObject result = post(endpoint, request);

                int importedMachines = 0;
                int importedInterventions = 0;
                Map<String, String> localMachineIds = new HashMap<>();
                JSONArray machines = result.optJSONArray("machines");
                if (machines != null) {
                    for (int i = 0; i < machines.length(); i++) {
                        JSONObject source = machines.getJSONObject(i);
                        String cloudId = source.optString("id", "");
                        DatabaseHelper.Machine before = db.findMachineByName(source.optString("name", ""));
                        DatabaseHelper.Machine local = db.importMachineFromCloud(
                                cloudId, source.optString("name", ""), source.optString("createdAt", ""));
                        if (local != null) {
                            localMachineIds.put(cloudId, local.id);
                            if (before == null) importedMachines++;
                        }
                    }
                }

                JSONArray interventions = result.optJSONArray("interventions");
                if (interventions != null) {
                    for (int i = 0; i < interventions.length(); i++) {
                        JSONObject source = interventions.getJSONObject(i);
                        String cloudMachineId = source.optString("machineId", "");
                        String localMachineId = localMachineIds.get(cloudMachineId);
                        if (localMachineId == null) {
                            DatabaseHelper.Machine byName = db.findMachineByName(source.optString("machineName", ""));
                            if (byName != null) localMachineId = byName.id;
                        }
                        if (localMachineId == null) continue;
                        if (db.importInterventionFromCloud(
                                source.optString("id", ""), localMachineId,
                                source.optString("machineName", ""), source.optString("date", ""),
                                source.optString("startTime", ""), source.optString("endTime", ""),
                                source.optInt("durationMinutes", 0), source.optString("description", ""),
                                source.optString("notes", ""), source.optString("createdAt", ""))) {
                            importedInterventions++;
                        }
                    }
                }

                int finalMachines = importedMachines;
                int finalInterventions = importedInterventions;
                mainHandler.post(() -> callback.onComplete(true,
                        "Ripristino completato.\nMacchinari importati: " + finalMachines +
                                "\nInterventi importati: " + finalInterventions));
            } catch (Exception ex) {
                String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                mainHandler.post(() -> callback.onComplete(false,
                        "Ripristino non riuscito. Nessun dato locale è stato cancellato.\n" + detail));
            }
        });
    }

    private JSONObject post(String endpoint, JSONObject json) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String response = readFully(stream);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            throw new Exception("Errore Google HTTP " + status + ": " + response);
        }
        JSONObject result = new JSONObject(response);
        if (!result.optBoolean("ok", false)) {
            throw new Exception(result.optString("error", "Risposta Google non valida"));
        }
        return result;
    }

    private String readFully(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }
}
