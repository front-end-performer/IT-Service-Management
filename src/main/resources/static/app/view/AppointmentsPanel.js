/**
 * Staff view: all appointments of a provider, filterable by period and service, with details.
 * Times are shown in the provider's local time zone; the list is paged (X-Total-Count).
 */
Ext.define('Appointments.view.AppointmentsPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'appointmentspanel',

    title: Appointments.i18n.t('appointments.title'),
    layout: {type: 'vbox', align: 'stretch'},
    bodyPadding: '32 24 24',

    initComponent: function () {
        var me = this, t = Appointments.i18n.t;
        me.page = 0;
        me.serviceStore = Ext.create('Ext.data.Store', {fields: ['id', 'label']});
        me.appointmentStore = Ext.create('Ext.data.Store', {
            fields: ['id', 'customerNumber', 'start', 'end', 'repairServiceId', 'repairServiceName', 'source']
        });

        me.items = [{
            xtype: 'component',
            margin: '0 0 20 0',
            html: '<h1 class="app-page-title">' + t('appointments.title') + '</h1>'
        }, {
            xtype: 'form',
            itemId: 'filterForm',
            cls: 'app-card',
            border: false,
            margin: 1, // room for the card's outline
            bodyPadding: '16 20',
            layout: {type: 'hbox', align: 'end'},
            defaults: {labelAlign: 'top', margin: '0 16 0 0'},
            items: [{
                xtype: 'providercombo',
                itemId: 'provider',
                fieldLabel: t('appointments.provider'),
                width: null,
                flex: 2,
                listeners: {change: me.onProviderChange, scope: me}
            }, {
                xtype: 'datefield', itemId: 'fromDay', fieldLabel: t('common.from'), flex: 1, format: 'Y-m-d',
                emptyText: t('appointments.anyDate')
            }, {
                xtype: 'datefield', itemId: 'toDay', fieldLabel: t('common.to'), flex: 1, format: 'Y-m-d',
                emptyText: t('appointments.anyDate')
            }, {
                xtype: 'combo', itemId: 'service', fieldLabel: t('common.service'), flex: 1.2,
                store: me.serviceStore, queryMode: 'local', editable: false, displayField: 'label', valueField: 'id',
                emptyText: t('appointments.allServices')
            }, {
                xtype: 'button', cls: 'app-btn-secondary', scale: 'medium', margin: '0 8 0 0',
                text: t('appointments.reset'), handler: me.resetFilters, scope: me
            }, {
                xtype: 'button', cls: 'app-btn-primary', scale: 'medium', margin: 0,
                text: t('appointments.search'), iconCls: 'x-fa fa-search', handler: me.search, scope: me
            }]
        }, {
            xtype: 'container',
            flex: 1,
            margin: '20 0 0 0',
            layout: {type: 'hbox', align: 'stretch'},
            items: [{
                xtype: 'grid',
                itemId: 'grid',
                cls: 'app-card',
                border: false,
                margin: 1, // room for the card's outline
                flex: 1,
                store: me.appointmentStore,
                emptyText: t('appointments.empty'),
                dockedItems: [{
                    xtype: 'toolbar',
                    dock: 'bottom',
                    cls: 'app-footbar',
                    padding: '12 16',
                    items: [
                        {xtype: 'tbtext', itemId: 'pageInfo', cls: 'app-hint app-hint-13', text: ''},
                        '->',
                        {itemId: 'prevPage', cls: 'app-btn-icon', iconCls: 'x-fa fa-chevron-left', tooltip: t('appointments.prevPage'),
                            ariaLabel: t('appointments.prevPage'), disabled: true, handler: function () { me.goToPage(me.page - 1); }},
                        {itemId: 'nextPage', cls: 'app-btn-icon', iconCls: 'x-fa fa-chevron-right', tooltip: t('appointments.nextPage'),
                            ariaLabel: t('appointments.nextPage'), disabled: true, margin: 0, handler: function () { me.goToPage(me.page + 1); }}
                    ]
                }],
                columns: [
                    {text: t('appointments.date'), dataIndex: 'start', width: 160, tdCls: 'app-cell-strong', renderer: me.renderDate, scope: me},
                    {text: t('appointments.time'), itemId: 'timeColumn', dataIndex: 'start', width: 190, tdCls: 'app-cell-muted', renderer: me.renderTimeRange, scope: me},
                    {text: t('common.customer'), dataIndex: 'customerNumber', width: 120, renderer: Ext.String.htmlEncode},
                    {text: t('common.service'), dataIndex: 'repairServiceName', flex: 1, renderer: function (v, m, r) {
                        return '<span class="app-badge">' + Ext.String.htmlEncode(r.get('repairServiceId')) + '</span>' +
                            Ext.String.htmlEncode(v);
                    }},
                    {text: t('appointments.source'), dataIndex: 'source', width: 120, renderer: me.renderSource, scope: me}
                ],
                listeners: {selectionchange: me.showDetails, scope: me}
            }, {
                xtype: 'panel',
                itemId: 'details',
                cls: 'app-card',
                border: false,
                margin: '1 1 1 21', // 20px gap plus room for the card's outline
                title: t('appointments.details'),
                tools: [{
                    type: 'close',
                    tooltip: t('appointments.closeDetails'),
                    callback: function () { me.down('#grid').getSelectionModel().deselectAll(); }
                }],
                width: 360,
                bodyPadding: '16 24 24',
                scrollable: 'y',
                hidden: true, // shown only while a row is selected; the table takes the full width otherwise
                tpl: [
                    '<div class="app-details">',
                    '<div class="app-details-when"><b>{day} · {time}</b><span class="app-hint">{zone} · {utc} UTC</span></div>',
                    '<dl>',
                    '<dt>' + t('common.customer') + '</dt><dd>{customerNumber:htmlEncode}</dd>',
                    '<dt>' + t('common.service') + '</dt><dd>{repairServiceId:htmlEncode} – {repairServiceName:htmlEncode}</dd>',
                    '<dt>' + t('appointments.duration') + '</dt><dd>{duration} min</dd>',
                    '<dt>' + t('appointments.provider') + '</dt><dd>{serviceName:htmlEncode}</dd>',
                    '<dt>' + t('appointments.source') + '</dt><dd>{source}</dd>',
                    '<dt>ID</dt><dd class="app-id">{id:htmlEncode}</dd>',
                    '</dl></div>'
                ]
            }]
        }];
        me.callParent();
    },

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
        me.down('#timeColumn').setText(Appointments.i18n.t('appointments.timeIn', Ext.String.htmlEncode(combo.getTimeZone())));
        me.down('#service').clearValue();
        me.serviceStore.removeAll();
        if (providerId) {
            Appointments.Api.getServices(providerId).then(function (services) {
                me.serviceStore.loadData(services.map(function (s) {
                    return {id: s.id, label: s.code + ' – ' + s.name};
                }));
            }).catch(function (e) {
                Appointments.Api.showError(Appointments.i18n.t('services.loadFailed'), e);
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
        me.down('#details').hide();
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
            details.hide();
            return;
        }
        details.show();
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
