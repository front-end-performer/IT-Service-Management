/**
 * Staff view: all appointments of a provider, filterable by period and service, with details.
 * Times are shown in the provider's local time zone; the list is paged (X-Total-Count).
 */
Ext.define('Appointments.view.AppointmentsPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'appointmentspanel',

    title: 'Provider appointments',
    layout: 'border',

    initComponent: function () {
        var me = this;
        me.page = 0;
        me.serviceStore = Ext.create('Ext.data.Store', {fields: ['id', 'label']});
        me.appointmentStore = Ext.create('Ext.data.Store', {
            fields: ['id', 'customerNumber', 'start', 'end', 'repairServiceId', 'repairServiceName', 'source']
        });

        me.items = [{
            region: 'north',
            xtype: 'form',
            itemId: 'filterForm',
            bodyPadding: 10,
            border: false,
            layout: {type: 'hbox', align: 'middle'},
            defaults: {margin: '0 10 0 0'},
            items: [{
                xtype: 'providercombo',
                itemId: 'provider',
                listeners: {change: me.onProviderChange, scope: me}
            }, {
                xtype: 'datefield', itemId: 'fromDay', fieldLabel: 'From', labelWidth: 40, width: 165, format: 'Y-m-d'
            }, {
                xtype: 'datefield', itemId: 'toDay', fieldLabel: 'To', labelWidth: 25, width: 150, format: 'Y-m-d'
            }, {
                xtype: 'combo', itemId: 'service', fieldLabel: 'Service', labelWidth: 50, width: 260,
                store: me.serviceStore, queryMode: 'local', editable: false, displayField: 'label', valueField: 'id',
                emptyText: 'all services'
            }, {
                xtype: 'button', text: 'Search', iconCls: 'x-fa fa-filter', handler: me.search, scope: me
            }, {
                xtype: 'button', text: 'Reset', handler: me.resetFilters, scope: me
            }]
        }, {
            region: 'center',
            xtype: 'grid',
            itemId: 'grid',
            store: me.appointmentStore,
            emptyText: 'No appointments.',
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                items: [
                    {itemId: 'prevPage', iconCls: 'x-fa fa-chevron-left', tooltip: 'Previous page', disabled: true,
                        handler: function () { me.goToPage(me.page - 1); }},
                    {itemId: 'nextPage', iconCls: 'x-fa fa-chevron-right', tooltip: 'Next page', disabled: true,
                        handler: function () { me.goToPage(me.page + 1); }},
                    {xtype: 'tbtext', itemId: 'pageInfo', text: ''},
                    '->',
                    {xtype: 'tbtext', itemId: 'zoneInfo', text: ''}
                ]
            }],
            columns: [
                {text: 'Date', dataIndex: 'start', width: 140, renderer: me.renderDate, scope: me},
                {text: 'Time', itemId: 'timeColumn', dataIndex: 'start', width: 170, renderer: me.renderTimeRange, scope: me},
                {text: 'Customer', dataIndex: 'customerNumber', width: 120, renderer: Ext.String.htmlEncode},
                {text: 'Service', dataIndex: 'repairServiceName', flex: 1, renderer: function (v, m, r) {
                    return '<span class="app-badge">' + Ext.String.htmlEncode(r.get('repairServiceId')) + '</span>' +
                        Ext.String.htmlEncode(v);
                }},
                {text: 'Source', dataIndex: 'source', width: 110, renderer: me.renderSource, scope: me}
            ],
            listeners: {selectionchange: me.showDetails, scope: me}
        }, {
            region: 'east',
            xtype: 'panel',
            itemId: 'details',
            title: 'Appointment details',
            width: 360,
            split: true,
            collapsible: true,
            bodyPadding: 16,
            scrollable: 'y',
            html: me.NO_DETAILS,
            tpl: [
                '<div class="app-details">',
                '<div class="app-details-when"><b>{day} · {time}</b><span class="app-hint">{zone} · {utc} UTC</span></div>',
                '<dl>',
                '<dt>Customer</dt><dd>{customerNumber:htmlEncode}</dd>',
                '<dt>Service</dt><dd>{repairServiceId:htmlEncode} – {repairServiceName:htmlEncode}</dd>',
                '<dt>Duration</dt><dd>{duration} min</dd>',
                '<dt>Provider</dt><dd>{serviceName:htmlEncode}</dd>',
                '<dt>Source</dt><dd>{source}</dd>',
                '<dt>ID</dt><dd>{id:htmlEncode}</dd>',
                '</dl></div>'
            ]
        }];
        me.callParent();
    },

    NO_DETAILS: '<div class="app-hint">Select an appointment to see its details.</div>',

    SOURCES: {
        BOOKED: '<span class="app-chip app-chip-booked">Booked</span>',
        IMPORTED: '<span class="app-chip">Imported</span>'
    },

    renderDate: function (iso) {
        return Appointments.Api.formatDate(iso, this.down('#provider').getTimeZone());
    },

    /** "10:00–12:00" */
    renderTimeRange: function (iso, meta, record) {
        var Api = Appointments.Api, zone = this.down('#provider').getTimeZone();
        return Api.formatClock(iso, zone) + '–' + Api.formatClock(record.get('end'), zone);
    },

    renderSource: function (source) {
        return this.SOURCES[source] || Ext.String.htmlEncode(source || '');
    },

    onProviderChange: function (combo, providerId) {
        var me = this;
        me.down('#zoneInfo').setText(Ext.String.htmlEncode(combo.describeItems().join(' · ')));
        me.down('#timeColumn').setText('Time (' + Ext.String.htmlEncode(combo.getTimeZone()) + ')');
        me.down('#service').clearValue();
        me.serviceStore.removeAll();
        if (providerId) {
            Appointments.Api.getServices(providerId).then(function (services) {
                me.serviceStore.loadData(services.map(function (s) {
                    return {id: s.id, label: s.code + ' – ' + s.name};
                }));
            });
        }
        me.search();
    },

    /** New filter: back to the first page. */
    search: function () {
        this.goToPage(0);
    },

    goToPage: function (page) {
        this.page = Math.max(page, 0);
        this.reload();
    },

    resetFilters: function () {
        this.down('#fromDay').reset();
        this.down('#toDay').reset();
        this.down('#service').clearValue();
        this.search();
    },

    /** Reloads the current page; called after bookings and imports too. */
    reload: function () {
        var me = this, Api = Appointments.Api,
            providerId = me.down('#provider').getValue(),
            zone = me.down('#provider').getTimeZone(),
            toDay = me.down('#toDay').getValue();
        me.down('#details').update(me.NO_DETAILS);
        if (!providerId) {
            me.appointmentStore.removeAll();
            me.updatePaging(0);
            return;
        }
        me.setLoading(true);
        Api.getAppointments(providerId, {
            from: Api.toInstant(me.down('#fromDay').getValue(), null, zone),
            // The whole local "to" day is included: up to one second before the next local midnight.
            to: toDay ? new Date(Date.parse(Api.toInstant(Ext.Date.add(toDay, Ext.Date.DAY, 1), null, zone)) - 1000).toISOString() : null,
            repairServiceId: me.down('#service').getValue()
        }, me.page).then(function (result) {
            me.appointmentStore.loadData(result.items);
            me.updatePaging(result.total);
        }).catch(function (e) {
            Api.showError('Loading appointments failed', e);
        }).finally(function () {
            me.setLoading(false);
        });
    },

    updatePaging: function (total) {
        var size = Appointments.Api.PAGE_SIZE,
            first = total ? this.page * size + 1 : 0,
            last = Math.min((this.page + 1) * size, total);
        this.down('#pageInfo').setText(total ? first + '–' + last + ' of ' + total : '');
        this.down('#prevPage').setDisabled(this.page === 0);
        this.down('#nextPage').setDisabled(last >= total);
    },

    showDetails: function (grid, selection) {
        var me = this, Api = Appointments.Api, details = me.down('#details');
        if (!selection.length) {
            details.update(me.NO_DETAILS);
            return;
        }
        var zone = me.down('#provider').getTimeZone();
        Api.getAppointment(me.down('#provider').getValue(), selection[0].get('id')).then(function (a) {
            details.update(Ext.apply({}, {
                day: Api.formatDate(a.start, zone),
                time: Api.formatClock(a.start, zone) + '–' + Api.formatClock(a.end, zone),
                zone: Ext.String.htmlEncode(zone),
                utc: Api.formatClock(a.start, 'UTC') + '–' + Api.formatClock(a.end, 'UTC'),
                duration: (Date.parse(a.end) - Date.parse(a.start)) / 60000,
                source: me.renderSource(a.source)
            }, a)); // a's own fields are defaults; the formatted ones above win
        }).catch(function (e) {
            Api.showError('Loading details failed', e);
        });
    }
});
