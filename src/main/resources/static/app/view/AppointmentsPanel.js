/**
 * Staff view: all appointments of a provider, filterable by period and service, with details.
 * Times are shown in the provider's local time zone; the list is paged (X-Total-Count).
 */
Ext.define('Appointments.view.AppointmentsPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'appointmentspanel',

    title: Appointments.i18n.t('appointments.title'),
    layout: 'border',

    initComponent: function () {
        var me = this, t = Appointments.i18n.t;
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
                xtype: 'datefield', itemId: 'fromDay', fieldLabel: t('common.from'), labelWidth: 40, width: 165, format: 'Y-m-d'
            }, {
                xtype: 'datefield', itemId: 'toDay', fieldLabel: t('common.to'), labelWidth: 30, width: 155, format: 'Y-m-d'
            }, {
                xtype: 'combo', itemId: 'service', fieldLabel: t('common.service'), labelWidth: 100, width: 310,
                store: me.serviceStore, queryMode: 'local', editable: false, displayField: 'label', valueField: 'id',
                emptyText: t('appointments.allServices')
            }, {
                xtype: 'button', text: t('appointments.search'), iconCls: 'x-fa fa-filter', handler: me.search, scope: me
            }, {
                xtype: 'button', text: t('appointments.reset'), handler: me.resetFilters, scope: me
            }]
        }, {
            region: 'center',
            xtype: 'grid',
            itemId: 'grid',
            store: me.appointmentStore,
            emptyText: t('appointments.empty'),
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                items: [
                    {itemId: 'prevPage', iconCls: 'x-fa fa-chevron-left', tooltip: t('appointments.prevPage'), disabled: true,
                        handler: function () { me.goToPage(me.page - 1); }},
                    {itemId: 'nextPage', iconCls: 'x-fa fa-chevron-right', tooltip: t('appointments.nextPage'), disabled: true,
                        handler: function () { me.goToPage(me.page + 1); }},
                    {xtype: 'tbtext', itemId: 'pageInfo', text: ''},
                    '->',
                    {xtype: 'tbtext', itemId: 'zoneInfo', text: ''}
                ]
            }],
            columns: [
                {text: t('appointments.date'), dataIndex: 'start', width: 140, renderer: me.renderDate, scope: me},
                {text: t('appointments.time'), itemId: 'timeColumn', dataIndex: 'start', width: 170, renderer: me.renderTimeRange, scope: me},
                {text: t('common.customer'), dataIndex: 'customerNumber', width: 120, renderer: Ext.String.htmlEncode},
                {text: t('common.service'), dataIndex: 'repairServiceName', flex: 1, renderer: function (v, m, r) {
                    return '<span class="app-badge">' + Ext.String.htmlEncode(r.get('repairServiceId')) + '</span>' +
                        Ext.String.htmlEncode(v);
                }},
                {text: t('appointments.source'), dataIndex: 'source', width: 110, renderer: me.renderSource, scope: me}
            ],
            listeners: {selectionchange: me.showDetails, scope: me}
        }, {
            region: 'east',
            xtype: 'panel',
            itemId: 'details',
            title: t('appointments.details'),
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
                '<dt>' + t('common.customer') + '</dt><dd>{customerNumber:htmlEncode}</dd>',
                '<dt>' + t('common.service') + '</dt><dd>{repairServiceId:htmlEncode} – {repairServiceName:htmlEncode}</dd>',
                '<dt>' + t('appointments.duration') + '</dt><dd>{duration} min</dd>',
                '<dt>' + t('appointments.provider') + '</dt><dd>{serviceName:htmlEncode}</dd>',
                '<dt>' + t('appointments.source') + '</dt><dd>{source}</dd>',
                '<dt>ID</dt><dd>{id:htmlEncode}</dd>',
                '</dl></div>'
            ]
        }];
        me.callParent();
    },

    NO_DETAILS: '<div class="app-hint">' + Appointments.i18n.t('appointments.noDetails') + '</div>',

    SOURCES: {
        BOOKED: '<span class="app-chip app-chip-booked">' + Appointments.i18n.t('appointments.booked') + '</span>',
        IMPORTED: '<span class="app-chip">' + Appointments.i18n.t('appointments.imported') + '</span>'
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
        me.down('#timeColumn').setText(Appointments.i18n.t('appointments.timeIn', Ext.String.htmlEncode(combo.getTimeZone())));
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
            Api.showError(Appointments.i18n.t('appointments.loadFailed'), e);
        }).finally(function () {
            me.setLoading(false);
        });
    },

    updatePaging: function (total) {
        var size = Appointments.Api.PAGE_SIZE,
            first = total ? this.page * size + 1 : 0,
            last = Math.min((this.page + 1) * size, total);
        this.down('#pageInfo').setText(total ? Appointments.i18n.t('appointments.page', first, last, total) : '');
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
            Api.showError(Appointments.i18n.t('appointments.detailsFailed'), e);
        });
    }
});
