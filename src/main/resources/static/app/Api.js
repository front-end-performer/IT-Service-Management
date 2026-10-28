/**
 * Thin wrapper around the REST API (rest-api-spec.yml). All calls return Promises; failures are
 * rejected with the RFC 9457 "detail" text so views can show it directly.
 * All times are exchanged and displayed in UTC, like the API and the legacy CSV files.
 */
Ext.define('Appointments.Api', {
    singleton: true,

    request: function (method, url, body) {
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
                    throw new Error((json && (json.detail || json.title)) || ('HTTP ' + response.status));
                }
                return json;
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

    getAppointments: function (providerId, filter) {
        return this.request('GET', this.providerUrl(providerId) + '/appointments' + this.query(filter));
    },

    getAppointment: function (providerId, appointmentId) {
        return this.request('GET', this.providerUrl(providerId) + '/appointments/' + encodeURIComponent(appointmentId));
    },

    importCsv: function (providerId, file) {
        var form = new FormData();
        form.append('file', file);
        return this.request('POST', this.providerUrl(providerId) + '/appointment-imports', form);
    },

    /** Date (calendar day from a datefield) + optional time (timefield) -> ISO string in UTC. */
    toUtcIso: function (day, time) {
        if (!day) {
            return null;
        }
        var hours = time ? time.getHours() : 0, minutes = time ? time.getMinutes() : 0;
        return new Date(Date.UTC(day.getFullYear(), day.getMonth(), day.getDate(), hours, minutes)).toISOString();
    },

    /** "2026-11-02T09:00:00Z" -> "2026-11-02 09:00" (UTC, no local conversion). */
    formatUtc: function (iso) {
        return iso ? iso.substring(0, 16).replace('T', ' ') : '';
    },

    showError: function (title, error) {
        Ext.Msg.alert(title, Ext.String.htmlEncode(error.message || String(error)));
    }
});
