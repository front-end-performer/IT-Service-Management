/**
 * UI texts in English and German. The language follows the browser's first preferred language:
 * German for "de" / "de-*", English for everything else. There is no switch; it is fixed per page load.
 *
 * Each key holds [English, German] side by side, so a missing translation is visible at a glance.
 * Texts may contain {0}, {1} … placeholders (Ext.String.format) and HTML; callers encode their arguments.
 * API error messages are not translated: they stay English and appear under a translated title.
 */
(function () {
    var preferred = (navigator.languages && navigator.languages[0]) || navigator.language || '',
        lang = /^de(-|$)/i.test(preferred) ? 'de' : 'en',
        messages = {
            'app.title': ['IT Service Appointments', 'IT-Service-Termine'],

            'common.from': ['From', 'Von'],
            'common.to': ['To', 'Bis'],
            'common.service': ['Service', 'Dienstleistung'],
            'common.customer': ['Customer', 'Kunde'],

            'provider.label': ['IT service provider', 'IT-Service-Anbieter'],
            'provider.empty': ['Select a provider', 'Anbieter auswählen'],
            'provider.capacity.one': ['{0} appointment at a time', '{0} Termin gleichzeitig'],
            'provider.capacity.other': ['{0} appointments at a time', '{0} Termine gleichzeitig'],
            'provider.zone': ['Times in {0}', 'Zeiten in {0}'],
            'provider.loadFailed': ['Loading providers failed', 'Anbieter konnten nicht geladen werden'],
            'hours.always': ['open around the clock', 'rund um die Uhr geöffnet'],

            'booking.title': ['Book appointment', 'Termin buchen'],
            'booking.startHint': ['Choose provider, service and time window, then search.',
                'Anbieter, Dienstleistung und Zeitraum wählen, dann suchen.'],
            'booking.nothingFound': ['No free start times in this window. Try a longer window or another day.',
                'Keine freien Startzeiten in diesem Zeitraum. Einen längeren Zeitraum oder einen anderen Tag versuchen.'],
            'booking.formTitle': ['Find a slot', 'Termin finden'],
            'booking.serviceEmpty': ['Select a service', 'Dienstleistung auswählen'],
            'booking.rangeHint': ['Up to 31 days. An appointment must end by the "To" time.',
                'Bis zu 31 Tage. Ein Termin muss bis zur „Bis“-Zeit enden.'],
            'booking.search': ['Find available slots', 'Freie Termine suchen'],
            'booking.slots': ['Available slots', 'Freie Termine'],
            'booking.slotCount.one': ['Available slots · {0} start time', 'Freie Termine · {0} Startzeit'],
            'booking.slotCount.other': ['Available slots · {0} start times', 'Freie Termine · {0} Startzeiten'],
            'booking.slotInfo': ['Appointments start every 15 minutes. A time is listed only if the provider has a ' +
                'free place for the whole duration of the service.',
                'Termine beginnen alle 15 Minuten. Eine Zeit erscheint nur, wenn der Anbieter für die ganze Dauer ' +
                'der Dienstleistung einen freien Platz hat.'],
            'booking.slotInfoService': ['Appointments start every 15 minutes. A time is listed only if the provider ' +
                'has a free place for the whole {0} min of {1}.',
                'Termine beginnen alle 15 Minuten. Eine Zeit erscheint nur, wenn der Anbieter für {1} die ganzen ' +
                '{0} min einen freien Platz hat.'],
            'booking.slotAria': ['{0} to {1}', '{0} bis {1}'],
            'booking.serviceLabel': ['{0} – {1} ({2} min)', '{0} – {1} ({2} min)'],
            'booking.customerNumber': ['Customer number', 'Kundennummer'],
            'booking.customerEmpty': ['e.g. 10001', 'z. B. 10001'],
            'booking.book': ['Book appointment', 'Termin buchen'],
            'booking.servicesFailed': ['Loading services failed', 'Dienstleistungen konnten nicht geladen werden'],
            'booking.searching': ['Searching…', 'Suche läuft…'],
            'booking.searchFailed': ['Search failed', 'Suche fehlgeschlagen'],
            'booking.recheck': ['Availability is checked again when you book.',
                'Die Verfügbarkeit wird beim Buchen erneut geprüft.'],
            'booking.noSlot': ['No slot selected', 'Kein Termin ausgewählt'],
            'booking.booking': ['Booking…', 'Buchung läuft…'],
            'booking.booked': ['<b>Appointment booked</b>{0} for customer {1}. Appointment id: {2}',
                '<b>Termin gebucht</b>{0} für Kunde {1}. Termin-ID: {2}'],
            'booking.duplicate': ['<b>Already booked</b>This customer already has {0}. Nothing new was booked.',
                '<b>Bereits gebucht</b>Dieser Kunde hat bereits {0}. Es wurde nichts Neues gebucht.'],
            'booking.taken': ['<b>This slot was just taken</b>{0} was booked by someone else a moment ago. ' +
                'Nothing was booked for you. The list below is up to date, please pick another time.',
                '<b>Dieser Termin wurde gerade vergeben</b>{0} wurde soeben von jemand anderem gebucht. ' +
                'Für Sie wurde nichts gebucht. Die Liste unten ist aktuell, bitte eine andere Zeit wählen.'],
            'booking.busy': ['<b>The provider is busy</b>Nothing was booked. Please try again in a moment.',
                '<b>Der Anbieter ist ausgelastet</b>Es wurde nichts gebucht. Bitte gleich noch einmal versuchen.'],
            'booking.failed': ['Booking failed', 'Buchung fehlgeschlagen'],

            'appointments.title': ['Provider appointments', 'Termine des Anbieters'],
            'appointments.allServices': ['all services', 'alle Dienstleistungen'],
            'appointments.search': ['Search', 'Suchen'],
            'appointments.reset': ['Reset', 'Zurücksetzen'],
            'appointments.empty': ['No appointments.', 'Keine Termine.'],
            'appointments.prevPage': ['Previous page', 'Vorherige Seite'],
            'appointments.nextPage': ['Next page', 'Nächste Seite'],
            'appointments.page': ['{0}–{1} of {2}', '{0}–{1} von {2}'],
            'appointments.date': ['Date', 'Datum'],
            'appointments.time': ['Time', 'Uhrzeit'],
            'appointments.timeIn': ['Time ({0})', 'Uhrzeit ({0})'],
            'appointments.source': ['Source', 'Herkunft'],
            'appointments.details': ['Appointment details', 'Termindetails'],
            'appointments.duration': ['Duration', 'Dauer'],
            'appointments.provider': ['Provider', 'Anbieter'],
            'appointments.noDetails': ['Select an appointment to see its details.',
                'Einen Termin auswählen, um die Details zu sehen.'],
            'appointments.booked': ['Booked', 'Gebucht'],
            'appointments.imported': ['Imported', 'Importiert'],
            'appointments.loadFailed': ['Loading appointments failed', 'Termine konnten nicht geladen werden'],
            'appointments.detailsFailed': ['Loading details failed', 'Details konnten nicht geladen werden'],

            'import.title': ['Import CSV', 'CSV importieren'],
            'import.formTitle': ['Import appointments', 'Termine importieren'],
            'import.provider': ['Import into provider', 'Importieren in Anbieter'],
            'import.file': ['CSV file', 'CSV-Datei'],
            'import.choose': ['Choose…', 'Auswählen…'],
            'import.format': ['<b>Expected format</b><br><code>KdnNr,APP_DATE,SERVICE</code><br>' +
                '<code>10001,2026-11-02T08:00Z,DIA</code><br>' +
                'APP_DATE is in UTC; the app shows it in the provider\'s time zone.',
                '<b>Erwartetes Format</b><br><code>KdnNr,APP_DATE,SERVICE</code><br>' +
                '<code>10001,2026-11-02T08:00Z,DIA</code><br>' +
                'APP_DATE ist in UTC; die Anwendung zeigt es in der Zeitzone des Anbieters.'],
            'import.checks': ['<b>Each row is checked on its own</b><ul class="app-checklist">' +
                '<li>3 columns, customer number not empty (max. 64 characters)</li>' +
                '<li>Valid timestamp on a 15-minute step</li>' +
                '<li>Service offered by this provider</li>' +
                '<li>Within opening hours</li>' +
                '<li>Capacity holds for the whole duration</li>' +
                '<li>Not already imported (skipped as a duplicate)</li>' +
                '</ul>Valid rows are imported; the others are listed with a reason.',
                '<b>Jede Zeile wird einzeln geprüft</b><ul class="app-checklist">' +
                '<li>3 Spalten, Kundennummer nicht leer (max. 64 Zeichen)</li>' +
                '<li>Gültiger Zeitstempel im 15-Minuten-Raster</li>' +
                '<li>Dienstleistung wird von diesem Anbieter angeboten</li>' +
                '<li>Innerhalb der Öffnungszeiten</li>' +
                '<li>Kapazität reicht für die ganze Dauer</li>' +
                '<li>Noch nicht importiert (sonst als Duplikat übersprungen)</li>' +
                '</ul>Gültige Zeilen werden importiert, die übrigen mit Grund aufgelistet.'],
            'import.import': ['Import', 'Importieren'],
            'import.importing': ['Importing…', 'Import läuft…'],
            'import.lastImport': ['Last import', 'Letzter Import'],
            'import.lastImportInto': ['Last import · {0} into {1}', 'Letzter Import · {0} in {1}'],
            'import.lastImportFailed': ['Last import · failed', 'Letzter Import · fehlgeschlagen'],
            'import.none': ['No file imported yet.', 'Noch keine Datei importiert.'],
            'import.counters': ['<span>{0} rows</span><span class="ok"><b>{1}</b> imported</span>' +
                '<span>{2} duplicates skipped</span><span class="bad"><b>{3}</b> rejected</span>',
                '<span>{0} Zeilen</span><span class="ok"><b>{1}</b> importiert</span>' +
                '<span>{2} Duplikate übersprungen</span><span class="bad"><b>{3}</b> abgelehnt</span>'],
            'import.status.RUNNING': ['Import running', 'Import läuft'],
            'import.status.FAILED': ['Import failed', 'Import abgebrochen'],
            'import.failed': ['Import failed', 'Import fehlgeschlagen'],
            'import.rejectedRows': ['Rows not imported', 'Nicht importierte Zeilen'],
            'import.noRejected': ['No rejected rows.', 'Keine abgelehnten Zeilen.'],
            'import.line': ['Line', 'Zeile'],
            'import.reason': ['Reason', 'Grund'],
            'import.row': ['Row', 'Zeile im Original'],
            'import.message': ['Message', 'Meldung'],
            'import.reason.MALFORMED_ROW': ['Wrong column count', 'Falsche Spaltenzahl'],
            'import.reason.MISSING_FIELD': ['Missing field', 'Fehlendes Feld'],
            'import.reason.INVALID_CUSTOMER_NUMBER': ['Invalid customer number', 'Ungültige Kundennummer'],
            'import.reason.INVALID_DATE': ['Invalid date', 'Ungültiges Datum'],
            'import.reason.OFF_GRID': ['Not on 15-min step', 'Nicht im 15-Minuten-Raster'],
            'import.reason.OUTSIDE_OPENING_HOURS': ['Outside opening hours', 'Außerhalb der Öffnungszeiten'],
            'import.reason.UNKNOWN_SERVICE_CODE': ['Unknown service', 'Unbekannte Dienstleistung'],
            'import.reason.CAPACITY_EXCEEDED': ['Capacity exceeded', 'Kapazität überschritten'],
            'import.reason.DUPLICATE': ['Duplicate, skipped', 'Duplikat, übersprungen']
        };

    Ext.define('Appointments.i18n', {
        singleton: true,

        /** 'en' or 'de'. */
        lang: lang,

        /** Locale for Intl date formatting. */
        locale: lang === 'de' ? 'de-DE' : 'en-GB',

        /** Text for `key` in the page language, with {0} … replaced by the further arguments; unknown keys show as is. */
        t: function (key) {
            var pair = messages[key],
                text = pair ? pair[lang === 'de' ? 1 : 0] : key;
            return arguments.length > 1
                ? Ext.String.format.apply(Ext.String, [text].concat(Array.prototype.slice.call(arguments, 1)))
                : text;
        }
    });

    document.documentElement.lang = lang;

    if (lang !== 'de') {
        return;
    }

    // Ext JS's own texts that this UI shows (the Ext locale package is not bundled).
    var monthNames = [], dayNames = [], i;
    for (i = 0; i < 12; i++) {
        monthNames.push(new Intl.DateTimeFormat('de-DE', {month: 'long', timeZone: 'UTC'}).format(Date.UTC(2024, i, 1)));
    }
    for (i = 0; i < 7; i++) { // 7 January 2024 was a Sunday, Ext's first day name
        dayNames.push(new Intl.DateTimeFormat('de-DE', {weekday: 'long', timeZone: 'UTC'}).format(Date.UTC(2024, 0, 7 + i)));
    }
    Ext.Date.monthNames = monthNames;
    Ext.Date.dayNames = dayNames;

    Ext.define('Appointments.locale.de.Text', {
        override: 'Ext.form.field.Text',
        blankText: 'Dieses Feld ist erforderlich',
        maxLengthText: 'Höchstens {0} Zeichen'
    });
    Ext.define('Appointments.locale.de.Date', {
        override: 'Ext.form.field.Date',
        invalidText: '{0} ist kein gültiges Datum, erwartet wird {1}'
    });
    Ext.define('Appointments.locale.de.Time', {
        override: 'Ext.form.field.Time',
        invalidText: '{0} ist keine gültige Uhrzeit'
    });
    Ext.define('Appointments.locale.de.DatePicker', {
        override: 'Ext.picker.Date',
        todayText: 'Heute',
        todayTip: '{0} (Leertaste)',
        nextText: 'Nächster Monat (Strg+Rechts)',
        prevText: 'Vorheriger Monat (Strg+Links)',
        monthYearText: 'Monat wählen (Strg+Hoch/Runter: Jahr wechseln)',
        startDay: 1
    });
    Ext.define('Appointments.locale.de.MonthPicker', {
        override: 'Ext.picker.Month',
        cancelText: 'Abbrechen'
    });
    Ext.define('Appointments.locale.de.LoadMask', {
        override: 'Ext.LoadMask',
        msg: 'Lädt…'
    });
    Ext.define('Appointments.locale.de.Panel', {
        override: 'Ext.panel.Panel',
        collapseToolText: 'Einklappen',
        expandToolText: 'Ausklappen'
    });
    Ext.define('Appointments.locale.de.GridHeader', {
        override: 'Ext.grid.header.Container',
        sortAscText: 'Aufsteigend sortieren',
        sortDescText: 'Absteigend sortieren',
        columnsText: 'Spalten'
    });
})();
