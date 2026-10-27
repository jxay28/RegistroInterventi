package it.registrointerventi;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "registro_interventi.db";
    private static final int DB_VERSION = 2;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE machines (" +
                "id TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL COLLATE NOCASE UNIQUE, " +
                "created_at TEXT NOT NULL, " +
                "synced INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE interventions (" +
                "id TEXT PRIMARY KEY, " +
                "machine_id TEXT NOT NULL, " +
                "machine_name TEXT NOT NULL, " +
                "work_date TEXT NOT NULL, " +
                "start_time TEXT NOT NULL, " +
                "end_time TEXT NOT NULL, " +
                "duration_minutes INTEGER NOT NULL, " +
                "description TEXT NOT NULL, " +
                "notes TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, " +
                "synced INTEGER NOT NULL DEFAULT 0, " +
                "cloud_synced INTEGER NOT NULL DEFAULT 0, " +
                "deleted INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE interventions ADD COLUMN cloud_synced INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE interventions ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE interventions SET cloud_synced = synced");
        }
    }

    public Machine addMachine(String name, String createdAt) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) return null;
        Machine existing = findMachineByName(clean);
        if (existing != null) return existing;

        Machine machine = new Machine(UUID.randomUUID().toString(), clean, createdAt, false);
        ContentValues values = new ContentValues();
        values.put("id", machine.id);
        values.put("name", machine.name);
        values.put("created_at", machine.createdAt);
        values.put("synced", 0);
        long result = getWritableDatabase().insert("machines", null, values);
        return result == -1 ? null : machine;
    }

    public Machine findMachineByName(String name) {
        try (Cursor c = getReadableDatabase().query(
                "machines", null, "name = ? COLLATE NOCASE", new String[]{name},
                null, null, null, "1")) {
            return c.moveToFirst() ? machineFromCursor(c) : null;
        }
    }

    public Machine findMachineById(String id) {
        try (Cursor c = getReadableDatabase().query(
                "machines", null, "id = ?", new String[]{id},
                null, null, null, "1")) {
            return c.moveToFirst() ? machineFromCursor(c) : null;
        }
    }

    public Machine importMachineFromCloud(String id, String name, String createdAt) {
        String cleanName = name == null ? "" : name.trim();
        if (id == null || id.trim().isEmpty() || cleanName.isEmpty()) return null;

        Machine existing = findMachineById(id);
        if (existing == null) existing = findMachineByName(cleanName);
        if (existing != null) {
            ContentValues synced = new ContentValues();
            synced.put("synced", 1);
            getWritableDatabase().update("machines", synced, "id = ?", new String[]{existing.id});
            return new Machine(existing.id, existing.name, existing.createdAt, true);
        }

        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", cleanName);
        values.put("created_at", createdAt == null ? "" : createdAt);
        values.put("synced", 1);
        long result = getWritableDatabase().insert("machines", null, values);
        return result == -1 ? null : new Machine(id, cleanName, createdAt, true);
    }

    public List<Machine> getMachines() {
        List<Machine> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "machines", null, null, null, null, null, "name COLLATE NOCASE")) {
            while (c.moveToNext()) result.add(machineFromCursor(c));
        }
        return result;
    }

    public List<Machine> getPendingMachines() {
        List<Machine> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "machines", null, "synced = 0", null, null, null, "created_at")) {
            while (c.moveToNext()) result.add(machineFromCursor(c));
        }
        return result;
    }

    public boolean deleteMachine(String id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM interventions WHERE machine_id = ?", new String[]{id})) {
            if (c.moveToFirst() && c.getInt(0) > 0) return false;
        }
        return getWritableDatabase().delete("machines", "id = ?", new String[]{id}) > 0;
    }

    public void markMachineSynced(String id) {
        ContentValues values = new ContentValues();
        values.put("synced", 1);
        getWritableDatabase().update("machines", values, "id = ?", new String[]{id});
    }

    public Intervention addIntervention(String machineId, String machineName, String date,
                                        String startTime, String endTime, int durationMinutes,
                                        String description, String notes, String createdAt) {
        Intervention item = new Intervention(UUID.randomUUID().toString(), machineId, machineName,
                date, startTime, endTime, durationMinutes, description, notes, createdAt,
                false, false, false);
        ContentValues values = interventionValues(item);
        values.put("synced", 0);
        values.put("cloud_synced", 0);
        values.put("deleted", 0);
        long result = getWritableDatabase().insert("interventions", null, values);
        return result == -1 ? null : item;
    }

    public boolean updateIntervention(String id, String machineId, String machineName, String date,
                                      String startTime, String endTime, int durationMinutes,
                                      String description, String notes) {
        ContentValues values = new ContentValues();
        values.put("machine_id", machineId);
        values.put("machine_name", machineName);
        values.put("work_date", date);
        values.put("start_time", startTime);
        values.put("end_time", endTime);
        values.put("duration_minutes", durationMinutes);
        values.put("description", description);
        values.put("notes", notes);
        values.put("synced", 0);
        return getWritableDatabase().update("interventions", values,
                "id = ? AND deleted = 0", new String[]{id}) > 0;
    }

    public boolean importInterventionFromCloud(String id, String machineId, String machineName,
                                               String date, String startTime, String endTime,
                                               int durationMinutes, String description,
                                               String notes, String createdAt) {
        if (id == null || id.trim().isEmpty() || machineId == null || machineId.trim().isEmpty()) {
            return false;
        }
        try (Cursor c = getReadableDatabase().query(
                "interventions", new String[]{"id"}, "id = ?", new String[]{id},
                null, null, null, "1")) {
            if (c.moveToFirst()) return false;
        }

        Intervention item = new Intervention(id, machineId, safe(machineName), safe(date), safe(startTime),
                safe(endTime), durationMinutes, safe(description), safe(notes), safe(createdAt),
                true, true, false);
        ContentValues values = interventionValues(item);
        values.put("synced", 1);
        values.put("cloud_synced", 1);
        values.put("deleted", 0);
        return getWritableDatabase().insert("interventions", null, values) != -1;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public List<Intervention> getHistory() {
        List<Intervention> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "interventions", null, "deleted = 0", null, null, null,
                "work_date DESC, start_time DESC, created_at DESC")) {
            while (c.moveToNext()) result.add(interventionFromCursor(c));
        }
        return result;
    }

    public List<Intervention> getPendingInterventions() {
        List<Intervention> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                "interventions", null, "synced = 0", null, null, null, "created_at")) {
            while (c.moveToNext()) result.add(interventionFromCursor(c));
        }
        return result;
    }

    public void markInterventionSynced(String id) {
        ContentValues values = new ContentValues();
        values.put("synced", 1);
        values.put("cloud_synced", 1);
        getWritableDatabase().update("interventions", values,
                "id = ? AND deleted = 0", new String[]{id});
    }

    public boolean requestDeleteIntervention(Intervention item) {
        if (!item.cloudSynced) {
            return hardDeleteIntervention(item.id);
        }
        ContentValues values = new ContentValues();
        values.put("deleted", 1);
        values.put("synced", 0);
        return getWritableDatabase().update("interventions", values,
                "id = ?", new String[]{item.id}) > 0;
    }

    public boolean hardDeleteIntervention(String id) {
        return getWritableDatabase().delete("interventions", "id = ?", new String[]{id}) > 0;
    }

    public int pendingCount() {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT (SELECT COUNT(*) FROM machines WHERE synced=0) + " +
                        "(SELECT COUNT(*) FROM interventions WHERE synced=0)", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    private ContentValues interventionValues(Intervention item) {
        ContentValues values = new ContentValues();
        values.put("id", item.id);
        values.put("machine_id", item.machineId);
        values.put("machine_name", item.machineName);
        values.put("work_date", item.workDate);
        values.put("start_time", item.startTime);
        values.put("end_time", item.endTime);
        values.put("duration_minutes", item.durationMinutes);
        values.put("description", item.description);
        values.put("notes", item.notes);
        values.put("created_at", item.createdAt);
        return values;
    }

    private Machine machineFromCursor(Cursor c) {
        return new Machine(
                c.getString(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getString(c.getColumnIndexOrThrow("created_at")),
                c.getInt(c.getColumnIndexOrThrow("synced")) == 1);
    }

    private Intervention interventionFromCursor(Cursor c) {
        return new Intervention(
                c.getString(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("machine_id")),
                c.getString(c.getColumnIndexOrThrow("machine_name")),
                c.getString(c.getColumnIndexOrThrow("work_date")),
                c.getString(c.getColumnIndexOrThrow("start_time")),
                c.getString(c.getColumnIndexOrThrow("end_time")),
                c.getInt(c.getColumnIndexOrThrow("duration_minutes")),
                c.getString(c.getColumnIndexOrThrow("description")),
                c.getString(c.getColumnIndexOrThrow("notes")),
                c.getString(c.getColumnIndexOrThrow("created_at")),
                c.getInt(c.getColumnIndexOrThrow("synced")) == 1,
                c.getInt(c.getColumnIndexOrThrow("cloud_synced")) == 1,
                c.getInt(c.getColumnIndexOrThrow("deleted")) == 1);
    }

    public static class Machine {
        public final String id;
        public final String name;
        public final String createdAt;
        public final boolean synced;

        public Machine(String id, String name, String createdAt, boolean synced) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.synced = synced;
        }

        @Override public String toString() { return name; }
    }

    public static class Intervention {
        public final String id;
        public final String machineId;
        public final String machineName;
        public final String workDate;
        public final String startTime;
        public final String endTime;
        public final int durationMinutes;
        public final String description;
        public final String notes;
        public final String createdAt;
        public final boolean synced;
        public final boolean cloudSynced;
        public final boolean deleted;

        public Intervention(String id, String machineId, String machineName, String workDate,
                            String startTime, String endTime, int durationMinutes,
                            String description, String notes, String createdAt, boolean synced,
                            boolean cloudSynced, boolean deleted) {
            this.id = id;
            this.machineId = machineId;
            this.machineName = machineName;
            this.workDate = workDate;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
            this.description = description;
            this.notes = notes;
            this.createdAt = createdAt;
            this.synced = synced;
            this.cloudSynced = cloudSynced;
            this.deleted = deleted;
        }
    }
}
