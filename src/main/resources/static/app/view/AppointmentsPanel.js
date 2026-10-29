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
            fields: ['id', 'customerNumber', 'start', 'end', 'repairServiceId', 'repairServiceName', 'source'],
            listeners: {datachanged: me.updateTable, clear: me.updateTable, scope: me}
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
            // Filters wait for a provider: appointments are listed per provider, see onProviderChange.
            defaults: {labelAlign: 'top', margin: '0 16 0 0', disabled: true},
            items: [{
                xtype: 'providercombo',
                itemId: 'provider',
                fieldLabel: t('appointments.provider'),
                width: null,
                flex: 2,
                maxWidth: 300,
                disabled: false,
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
                xtype: 'button', itemId: 'resetButton', cls: 'app-btn-secondary', scale: 'medium', margin: '0 8 0 0',
                text: t('appointments.reset'), handler: me.resetFilters, scope: me
            }, {
                xtype: 'button', itemId: 'searchButton', cls: 'app-btn-primary', scale: 'medium', margin: 0,
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
                cls: 'app-card app-grid-centered-empty app-quiet-mask',
                border: false,
                margin: 1, // room for the card's outline
                flex: 1,
                store: me.appointmentStore,
                emptyText: me.emptyHtml('appointments.noProvider'),
                dockedItems: [{
                    xtype: 'toolbar',
                    itemId: 'pagingBar',
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
                // The column takes the table's height; the card in it only its content's, and scrolls when that is more.
                xtype: 'container',
                itemId: 'detailsColumn',
                width: 362,
                margin: '0 0 0 20',
                layout: {type: 'vbox', align: 'stretch'},
                hidden: true, // shown only while a row is selected; the table takes the full width otherwise
                listeners: {
                    resize: function (column, width, height) {
                        column.down('#details').setMaxHeight(height - 2); // minus the margin around the card
                    }
                },
                items: [{
                    xtype: 'panel',
                    itemId: 'details',
                    cls: 'app-card',
                    border: false,
                    margin: 1, // room for the card's outline
                    title: t('appointments.details'),
                    tools: [{
                        type: 'close',
                        tooltip: t('appointments.closeDetails'),
                        callback: function () { me.down('#grid').getSelectionModel().deselectAll(); }
                    }],
                    bodyPadding: '16 24 24',
                    scrollable: 'y',
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
            }]
        }];
        me.callParent();
        me.updateTable();
    },

    emptyHtml: function (key) {
        return '<div class="app-empty">' + Appointments.i18n.t(key) + '</div>';
    },

    /** Without appointments the card shows only the empty text: no column headers, no paging bar. */
    updateTable: function () {
        var grid = this.down('#grid'), empty = !this.appointmentStore.getCount();
        grid.toggleCls('app-grid-is-empty', empty);
        grid.headerCt.setHidden(empty);
        grid.down('#pagingBar').setHidden(empty);
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
        Ext.Array.each(['#fromDay', '#toDay', '#service', '#resetButton', '#searchButton'], function (id) {
            me.down(id).setDisabled(!providerId);
        });
        me.down('#grid').setEmptyText(me.emptyHtml(providerId ? 'appointments.empty' : 'appointments.noProvider'));
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
        var me = this, Api = Appointments.Api, grid = me.down('#grid'),
            providerId = me.down('#provider').getValue(),
            zone = me.down('#provider').getTimeZone(),
            toDay = me.down('#toDay').getValue();
        me.down('#detailsColumn').hide();
        if (!providerId) {
            me.loadRequest = (me.loadRequest || 0) + 1; // a load still running must not fill the table
            grid.setLoading(false);
            me.appointmentStore.removeAll();
            me.updatePaging(0);
            return;
        }
        // The filters stay usable while the table loads, so only the latest request may fill it.
        var request = me.loadRequest = (me.loadRequest || 0) + 1;
        grid.setLoading(true);
        Api.getAppointments(providerId, {
            from: Api.toInstant(me.down('#fromDay').getValue(), null, zone),
            // The whole local "to" day is included: up to one second before the next local midnight.
            to: toDay ? new Date(Date.parse(Api.toInstant(Ext.Date.add(toDay, Ext.Date.DAY, 1), null, zone)) - 1000).toISOString() : null,
            repairServiceId: me.down('#service').getValue()
        }, me.page).then(function (result) {
            if (request !== me.loadRequest) {
                return;
            }
            me.appointmentStore.loadData(result.items);
            me.updatePaging(result.total);
        }).catch(function (e) {
            if (request === me.loadRequest) {
                Api.showError(Appointments.i18n.t('appointments.loadFailed'), e);
            }
        }).finally(function () {
            if (request === me.loadRequest) {
                grid.setLoading(false);
            }
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
        var me = this, Api = Appointments.Api, details = me.down('#details'), column = me.down('#detailsColumn');
        if (!selection.length) {
            column.hide();
            return;
        }
        column.show();
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
