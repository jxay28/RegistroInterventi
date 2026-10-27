// Sostituisci questo valore con un codice lungo scelto da te.
// Inserisci lo stesso identico codice nelle impostazioni dell'app Android.
var SECRET_TOKEN = 'CAMBIA-QUESTO-CODICE-SEGRETO';
var FILE_NAME = 'Registro Interventi';

function doGet(e) {
  var parameters = e && e.parameter ? e.parameter : {};
  if (parameters.dashboard === '1') {
    if (parameters.token !== SECRET_TOKEN) {
      return HtmlService.createHtmlOutput('<h2>Accesso non autorizzato</h2>');
    }
    var template = HtmlService.createTemplateFromFile('Dashboard');
    template.dashboardToken = parameters.token;
    return template.evaluate()
      .setTitle('Riepilogo manutenzioni')
      .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL);
  }
  var spreadsheet = getOrCreateSpreadsheet_();
  return json_({
    ok: true,
    message: 'Registro Interventi è attivo',
    spreadsheetUrl: spreadsheet.getUrl()
  });
}

function getDashboardData(token) {
  if (token !== SECRET_TOKEN) throw new Error('Accesso non autorizzato');
  var spreadsheet = getOrCreateSpreadsheet_();
  var machineSheet = spreadsheet.getSheetByName('Macchinari');
  var interventionSheet = spreadsheet.getSheetByName('Interventi');
  var machines = [];
  var knownMachines = {};
  var interventions = [];

  if (machineSheet && machineSheet.getLastRow() >= 2) {
    var machineRows = machineSheet.getRange(2, 1, machineSheet.getLastRow() - 1, 3).getDisplayValues();
    for (var i = 0; i < machineRows.length; i++) {
      var machineName = String(machineRows[i][1] || '').trim();
      if (machineName && !knownMachines[machineName.toLowerCase()]) {
        knownMachines[machineName.toLowerCase()] = true;
        machines.push(machineName);
      }
    }
  }

  if (interventionSheet && interventionSheet.getLastRow() >= 2) {
    var rows = interventionSheet.getRange(2, 1, interventionSheet.getLastRow() - 1, 10).getDisplayValues();
    for (var r = 0; r < rows.length; r++) {
      var row = rows[r];
      var name = String(row[2] || '').trim();
      if (!name) continue;
      interventions.push({
        id: String(row[0] || ''),
        date: String(row[1] || ''),
        machine: name,
        startTime: String(row[3] || ''),
        endTime: String(row[4] || ''),
        durationMinutes: parseInt(row[5], 10) || 0,
        description: String(row[7] || ''),
        notes: String(row[8] || '')
      });
    }
  }

  machines.sort();
  interventions.sort(function(a, b) { return String(b.date).localeCompare(String(a.date)); });
  return {machines: machines, interventions: interventions, updatedAt: new Date().getTime()};
}

function doPost(e) {
  var lock = LockService.getScriptLock();
  try {
    if (!e || !e.postData || !e.postData.contents) {
      throw new Error('Richiesta vuota');
    }
    var data = JSON.parse(e.postData.contents);
    if (!SECRET_TOKEN || SECRET_TOKEN === 'CAMBIA-QUESTO-CODICE-SEGRETO') {
      throw new Error('Configura SECRET_TOKEN nello script');
    }
    if (data.token !== SECRET_TOKEN) {
      throw new Error('Codice segreto non valido');
    }

    lock.waitLock(20000);
    var spreadsheet = getOrCreateSpreadsheet_();
    if (data.type === 'machine') {
      saveMachine_(spreadsheet, data);
    } else if (data.type === 'intervention') {
      saveIntervention_(spreadsheet, data);
    } else if (data.type === 'delete_intervention') {
      deleteIntervention_(spreadsheet, data);
    } else if (data.type === 'restore') {
      return json_(buildRestoreData_(spreadsheet));
    } else {
      throw new Error('Tipo di dato non riconosciuto');
    }

    return json_({ok: true, spreadsheetUrl: spreadsheet.getUrl()});
  } catch (error) {
    return json_({ok: false, error: String(error.message || error)});
  } finally {
    try { lock.releaseLock(); } catch (_) {}
  }
}

function getOrCreateSpreadsheet_() {
  var properties = PropertiesService.getScriptProperties();
  var savedId = properties.getProperty('SPREADSHEET_ID');
  if (savedId) {
    try {
      return SpreadsheetApp.openById(savedId);
    } catch (_) {
      properties.deleteProperty('SPREADSHEET_ID');
    }
  }

  var spreadsheet = SpreadsheetApp.create(FILE_NAME);
  properties.setProperty('SPREADSHEET_ID', spreadsheet.getId());
  prepareInterventionsSheet_(spreadsheet.getSheets()[0]);
  prepareMachinesSheet_(spreadsheet.insertSheet('Macchinari'));
  return spreadsheet;
}

function prepareInterventionsSheet_(sheet) {
  sheet.setName('Interventi');
  sheet.clear();
  sheet.appendRow([
    'ID', 'Data', 'Macchinario', 'Ora inizio', 'Ora fine',
    'Durata (minuti)', 'Durata', 'Intervento eseguito', 'Note', 'Registrato il'
  ]);
  styleHeader_(sheet, 10);
  sheet.setFrozenRows(1);
  sheet.setColumnWidth(1, 250);
  sheet.setColumnWidth(2, 100);
  sheet.setColumnWidth(3, 190);
  sheet.setColumnWidth(8, 360);
  sheet.setColumnWidth(9, 300);
  sheet.getRange('A:A').setNumberFormat('@');
  sheet.getRange('B:B').setNumberFormat('@');
  sheet.getRange('D:E').setNumberFormat('@');
}

function prepareMachinesSheet_(sheet) {
  sheet.clear();
  sheet.appendRow(['ID', 'Macchinario', 'Inserito il']);
  styleHeader_(sheet, 3);
  sheet.setFrozenRows(1);
  sheet.setColumnWidth(1, 250);
  sheet.setColumnWidth(2, 240);
  sheet.setColumnWidth(3, 190);
  sheet.getRange('A:A').setNumberFormat('@');
}

function saveMachine_(spreadsheet, data) {
  var sheet = spreadsheet.getSheetByName('Macchinari') || spreadsheet.insertSheet('Macchinari');
  if (sheet.getLastRow() === 0) prepareMachinesSheet_(sheet);
  if (idExists_(sheet, String(data.id))) return;
  sheet.appendRow([String(data.id), String(data.name || ''), formatTimestamp_(data.createdAt)]);
}

function saveIntervention_(spreadsheet, data) {
  var sheet = spreadsheet.getSheetByName('Interventi') || spreadsheet.insertSheet('Interventi');
  if (sheet.getLastRow() === 0) prepareInterventionsSheet_(sheet);
  var minutes = Number(data.durationMinutes || 0);
  var values = [
    String(data.id),
    formatItalianDate_(String(data.date || '')),
    String(data.machineName || ''),
    String(data.startTime || ''),
    String(data.endTime || ''),
    minutes,
    formatDuration_(minutes),
    String(data.description || ''),
    String(data.notes || ''),
    formatTimestamp_(data.createdAt)
  ];
  var existingRow = findRowById_(sheet, String(data.id));
  var row;
  if (existingRow) {
    row = existingRow;
    sheet.getRange(row, 1, 1, values.length).setValues([values]);
  } else {
    sheet.appendRow(values);
    row = sheet.getLastRow();
  }
  sheet.getRange(row, 8, 1, 2).setWrap(true).setVerticalAlignment('top');
}

function deleteIntervention_(spreadsheet, data) {
  var sheet = spreadsheet.getSheetByName('Interventi');
  if (!sheet) return;
  var row = findRowById_(sheet, String(data.id));
  if (row) sheet.deleteRow(row);
}

function buildRestoreData_(spreadsheet) {
  var machines = [];
  var interventions = [];
  var machineIdsByName = {};
  var machineSheet = spreadsheet.getSheetByName('Macchinari');
  if (!machineSheet) {
    machineSheet = spreadsheet.insertSheet('Macchinari');
    prepareMachinesSheet_(machineSheet);
  }

  if (machineSheet && machineSheet.getLastRow() >= 2) {
    var machineRows = machineSheet.getRange(2, 1, machineSheet.getLastRow() - 1, 3).getDisplayValues();
    for (var i = 0; i < machineRows.length; i++) {
      var machineName = String(machineRows[i][1] || '').trim();
      if (!machineName) continue;
      var machineId = String(machineRows[i][0] || '').trim();
      if (!machineId) {
        machineId = 'machine-' + Utilities.getUuid();
        machineSheet.getRange(i + 2, 1).setValue(machineId);
      }
      machineIdsByName[machineName.toLowerCase()] = machineId;
      machines.push({
        id: machineId,
        name: machineName,
        createdAt: String(machineRows[i][2] || '')
      });
    }
  }

  var interventionSheet = spreadsheet.getSheetByName('Interventi');
  if (interventionSheet && interventionSheet.getLastRow() >= 2) {
    var rows = interventionSheet.getRange(2, 1, interventionSheet.getLastRow() - 1, 10).getDisplayValues();
    for (var r = 0; r < rows.length; r++) {
      var row = rows[r];
      var name = String(row[2] || '').trim();
      if (!name) continue;
      var key = name.toLowerCase();
      var linkedMachineId = machineIdsByName[key];
      if (!linkedMachineId) {
        linkedMachineId = 'machine-' + Utilities.getUuid();
        machineIdsByName[key] = linkedMachineId;
        machines.push({id: linkedMachineId, name: name, createdAt: ''});
        if (machineSheet) machineSheet.appendRow([linkedMachineId, name, '']);
      }

      var interventionId = String(row[0] || '').trim();
      if (!interventionId) {
        interventionId = 'intervention-' + Utilities.getUuid();
        interventionSheet.getRange(r + 2, 1).setValue(interventionId);
      }
      interventions.push({
        id: interventionId,
        machineId: linkedMachineId,
        machineName: name,
        date: toIsoDate_(String(row[1] || '')),
        startTime: String(row[3] || ''),
        endTime: String(row[4] || ''),
        durationMinutes: parseInt(row[5], 10) || 0,
        description: String(row[7] || ''),
        notes: String(row[8] || ''),
        createdAt: String(row[9] || '')
      });
    }
  }

  return {
    ok: true,
    spreadsheetUrl: spreadsheet.getUrl(),
    machines: machines,
    interventions: interventions
  };
}

function toIsoDate_(value) {
  var parts = value.split('/');
  if (parts.length === 3) return parts[2] + '-' + parts[1] + '-' + parts[0];
  return value;
}

function idExists_(sheet, id) {
  return findRowById_(sheet, id) !== 0;
}

function findRowById_(sheet, id) {
  if (!id || sheet.getLastRow() < 2) return false;
  var match = sheet.getRange(2, 1, sheet.getLastRow() - 1, 1)
    .createTextFinder(id).matchEntireCell(true).findNext();
  return match ? match.getRow() : 0;
}

function formatItalianDate_(isoDate) {
  var parts = isoDate.split('-');
  return parts.length === 3 ? parts[2] + '/' + parts[1] + '/' + parts[0] : isoDate;
}

function formatDuration_(minutes) {
  var hours = Math.floor(minutes / 60);
  var rest = minutes % 60;
  return pad2_(hours) + ':' + pad2_(rest);
}

function pad2_(value) {
  return value < 10 ? '0' + value : String(value);
}

function formatTimestamp_(value) {
  if (!value) return '';
  var date = new Date(value);
  if (isNaN(date.getTime())) return String(value);
  return Utilities.formatDate(date, Session.getScriptTimeZone(), 'dd/MM/yyyy HH:mm:ss');
}

function styleHeader_(sheet, columns) {
  sheet.getRange(1, 1, 1, columns)
    .setBackground('#155E75')
    .setFontColor('#FFFFFF')
    .setFontWeight('bold')
    .setHorizontalAlignment('center');
}

function json_(payload) {
  return ContentService.createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}

