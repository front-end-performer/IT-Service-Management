/**
 * Thin wrapper around the REST API (rest-api-spec.yml). All calls return Promises; failures are
 * rejected with the RFC 9457 "detail" text so views can show it directly, and the HTTP status in
 * error.status so views can tell e.g. a taken slot (409) from a busy provider (503).
 *
 * The API speaks UTC. The UI shows and accepts times in the selected provider's time zone
 * (GET /it-services → timeZone), so "08:00" means 08:00 at the provider, in winter and in summer.
 */
Ext.define('Appointments.Api', {
    singleton: true,

    PAGE_SIZE: 100,

    request: function (method, url, body) {
        return this.send(method, url, body).then(function (result) {
            return result.json;
        });
    },

    /** Like request(), but also resolves the response so callers can read headers. */
    send: function (method, url, body) {
        var init = {method: method, headers: {'Accept': 'application/json'}};
        if (body instanceof FormData) {
            init.body = body;
        } else if (body !== undefined) {
            init.headers['Content-Type'] = 'application/json';
            init.body = JSON.stringify(body);
        }
        return fetch(url, init).then(function (response) {
            return response.text().then(function (text) {
                var json = text ? JSON.parse(text) : null;
                if (!response.ok) {
                    var error = new Error((json && (json.detail || json.title)) || ('HTTP ' + response.status));
                    error.status = response.status;
                    throw error;
                }
                return {json: json, response: response};
            });
        });
    },

    providerUrl: function (providerId) {
        return '/it-services/' + encodeURIComponent(providerId);
    },

    query: function (params) {
        var parts = [];
        Ext.Object.each(params, function (key, value) {
            if (value !== null && value !== undefined && value !== '') {
                parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
            }
        });
        return parts.length ? '?' + parts.join('&') : '';
    },

    getProviders: function () {
        return this.request('GET', '/it-services');
    },

    getServices: function (providerId) {
        return this.request('GET', this.providerUrl(providerId) + '/services');
    },

    getSuggestions: function (providerId, repairServiceId, from, to) {
        return this.request('GET', this.providerUrl(providerId) + '/appointment-suggestions' +
            this.query({repairServiceId: repairServiceId, from: from, to: to}));
    },

    book: function (providerId, request) {
        return this.request('POST', this.providerUrl(providerId) + '/appointments', request);
    },

    /** Resolves {items, total}; the total comes from the X-Total-Count header. */
    getAppointments: function (providerId, filter, page) {
        var params = Ext.apply({page: page || 0, size: this.PAGE_SIZE}, filter);
        return this.send('GET', this.providerUrl(providerId) + '/appointments' + this.query(params))
            .then(function (result) {
                return {items: result.json, total: parseInt(result.response.headers.get('X-Total-Count'), 10)};
            });
    },

    getAppointment: function (providerId, appointmentId) {
        return this.request('GET', this.providerUrl(providerId) + '/appointments/' + encodeURIComponent(appointmentId));
    },

    importCsv: function (providerId, file) {
        var form = new FormData();
        form.append('file', file);
        return this.request('POST', this.providerUrl(providerId) + '/appointment-imports', form);
    },

    /** Wall-clock fields (year … minute, as strings) of an instant in a time zone. */
    zonedParts: function (date, zone) {
        var parts = {};
        new Intl.DateTimeFormat('en-GB', {
            timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
        }).formatToParts(date).forEach(function (p) {
            parts[p.type] = p.value;
        });
        return parts;
    },

    /** "2026-11-02T08:00:00Z" in Europe/Berlin -> "2026-11-02 09:00". */
    formatInZone: function (iso, zone) {
        if (!iso) {
            return '';
        }
        var p = this.zonedParts(new Date(iso), zone);
        return p.year + '-' + p.month + '-' + p.day + ' ' + p.hour + ':' + p.minute;
    },

    /** "Thu 29 Oct 2026" / "Do., 29. Okt. 2026" (default) or any other Intl date format, in the page language. */
    formatDate: function (iso, zone, options) {
        return new Intl.DateTimeFormat(Appointments.i18n.locale, Ext.apply({timeZone: zone},
            options || {weekday: 'short', day: 'numeric', month: 'short', year: 'numeric'})).format(new Date(iso));
    },

    /** "2026-11-02T08:00:00Z" in Europe/Berlin -> "09:00". */
    formatClock: function (iso, zone) {
        return this.formatInZone(iso, zone).substring(11);
    },

    /** Offset of the zone from UTC at the given instant, in milliseconds (e.g. +3600000 for CET). */
    offsetMillis: function (instantMillis, zone) {
        var p = this.zonedParts(new Date(instantMillis), zone);
        var wallAsUtc = Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour, +p.minute);
        return wallAsUtc - Math.floor(instantMillis / 60000) * 60000;
    },

    /**
     * Calendar day (datefield) + optional time (timefield), meant as wall-clock time in `zone`
     * -> ISO instant. The offset is applied twice so days with a DST switch resolve correctly.
     */
    toInstant: function (day, time, zone) {
        if (!day) {
            return null;
        }
        var wall = Date.UTC(day.getFullYear(), day.getMonth(), day.getDate(),
            time ? time.getHours() : 0, time ? time.getMinutes() : 0);
        var instant = wall - this.offsetMillis(wall, zone);
        instant = wall - this.offsetMillis(instant, zone);
        return new Date(instant).toISOString();
    },

    /** {MONDAY: "08:00-18:00", …} -> "Mon–Fri 08:00–18:00, Sat 09:00–13:00" ("Mo.–Fr. …" in German). */
    describeOpeningHours: function (openingHours) {
        var days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
            names = days.map(function (day, i) { // 1 January 2024 was a Monday
                return new Intl.DateTimeFormat(Appointments.i18n.locale, {weekday: 'short', timeZone: 'UTC'})
                    .format(Date.UTC(2024, 0, 1 + i));
            }),
            groups = [];
        if (!openingHours || Ext.Object.isEmpty(openingHours)) {
            return Appointments.i18n.t('hours.always');
        }
        days.forEach(function (day, i) {
            var hours = openingHours[day], last = groups[groups.length - 1];
            if (!hours) {
                return;
            }
            if (last && last.hours === hours && last.to === i - 1) {
                last.to = i;
            } else {
                groups.push({from: i, to: i, hours: hours});
            }
        });
        return groups.map(function (g) {
            return (g.from === g.to ? names[g.from] : names[g.from] + '–' + names[g.to]) + ' ' + g.hours.replace('-', '–');
        }).join(', ');
    },

    /**
     * Shows an API error. Server messages name times in UTC ("…at 2026-11-02T08:00:00Z"); with a
     * zone given, those are rewritten into the provider's local time like everything else on screen.
     */
    showError: function (title, error, zone) {
        Ext.Msg.alert(title, Ext.String.htmlEncode(this.errorText(error, zone)));
    },

    /** The error's message, with UTC times rewritten into `zone` (see showError). */
    errorText: function (error, zone) {
        var me = this, text = error.message || String(error);
        if (zone) {
            text = text.replace(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?Z/g, function (iso) {
                return me.formatInZone(iso, zone) + ' (' + zone + ')';
            });
        }
        return text;
    },

    /** Inline message box for a panel: kind is 'info', 'success', 'warn' or 'error'; text is HTML. */
    showBanner: function (banner, kind, html) {
        banner.setHtml('<div class="app-banner app-banner-' + kind + '">' + html + '</div>');
        banner.show();
    }
});
