/**
 * Staff view: all appointments of a provider, filterable by period and service, with details.
 */
Ext.define('Appointments.view.AppointmentsPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'appointmentspanel',

    title: 'Provider appointments',
    layout: 'border',

    initComponent: function () {
        var me = this;
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
                xtype: 'datefield', itemId: 'fromDay', fieldLabel: 'From (UTC)', labelWidth: 75, width: 200, format: 'Y-m-d'
            }, {
                xtype: 'datefield', itemId: 'toDay', fieldLabel: 'To (UTC)', labelWidth: 60, width: 185, format: 'Y-m-d'
            }, {
                xtype: 'combo', itemId: 'service', fieldLabel: 'Service', labelWidth: 50, width: 260,
                store: me.serviceStore, queryMode: 'local', editable: false, displayField: 'label', valueField: 'id',
                emptyText: 'all services'
            }, {
                xtype: 'button', text: 'Search', iconCls: 'x-fa fa-filter', handler: me.reload, scope: me
            }, {
                xtype: 'button', text: 'Reset', handler: me.resetFilters, scope: me
            }]
        }, {
            region: 'center',
            xtype: 'grid',
            itemId: 'grid',
            store: me.appointmentStore,
            emptyText: 'No appointments.',
            columns: [
                {text: 'Start (UTC)', dataIndex: 'start', width: 150, renderer: Appointments.Api.formatUtc},
                {text: 'End (UTC)', dataIndex: 'end', width: 150, renderer: Appointments.Api.formatUtc},
                {text: 'Customer', dataIndex: 'customerNumber', width: 120},
                {text: 'Service', dataIndex: 'repairServiceName', flex: 1, renderer: function (v, m, r) {
                    return Ext.String.htmlEncode(r.get('repairServiceId') + ' – ' + v);
                }},
                {text: 'Source', dataIndex: 'source', width: 100}
            ],
            listeners: {selectionchange: me.showDetails, scope: me}
        }, {
            region: 'east',
            xtype: 'form',
            itemId: 'details',
            title: 'Appointment details',
            width: 380,
            split: true,
            collapsible: true,
            bodyPadding: 10,
            defaults: {xtype: 'displayfield', labelWidth: 110, htmlEncode: true},
            items: [
                {fieldLabel: 'Appointment id', name: 'id'},
                {fieldLabel: 'Customer', name: 'customerNumber'},
                {fieldLabel: 'Start (UTC)', name: 'start'},
                {fieldLabel: 'End (UTC)', name: 'end'},
                {fieldLabel: 'Provider', name: 'serviceName'},
                {fieldLabel: 'Service', name: 'repairService'},
                {fieldLabel: 'Source', name: 'source'}
            ]
        }];
        me.callParent();
    },

    onProviderChange: function (combo, providerId) {
        var me = this;
        me.down('#service').clearValue();
        me.serviceStore.removeAll();
        if (providerId) {
            Appointments.Api.getServices(providerId).then(function (services) {
                me.serviceStore.loadData(services.map(function (s) {
                    return {id: s.id, label: s.code + ' – ' + s.name};
                }));
            });
        }
        me.reload();
    },

    resetFilters: function () {
        this.down('#fromDay').reset();
        this.down('#toDay').reset();
        this.down('#service').clearValue();
        this.reload();
    },

    /** Reloads the grid; called after bookings and imports too. */
    reload: function () {
        var me = this, Api = Appointments.Api,
            providerId = me.down('#provider').getValue(),
            toDay = me.down('#toDay').getValue();
        me.down('#details').reset();
        if (!providerId) {
            me.appointmentStore.removeAll();
            return;
        }
        me.setLoading(true);
        Api.getAppointments(providerId, {
            from: Api.toUtcIso(me.down('#fromDay').getValue()),
            // Whole "to" day is included.
            to: toDay ? new Date(Date.parse(Api.toUtcIso(toDay)) + 86399000).toISOString() : null,
            repairServiceId: me.down('#service').getValue()
        }).then(function (appointments) {
            me.appointmentStore.loadData(appointments);
        }).catch(function (e) {
            Api.showError('Loading appointments failed', e);
        }).finally(function () {
            me.setLoading(false);
        });
    },

    showDetails: function (grid, selection) {
        var me = this, Api = Appointments.Api, form = me.down('#details');
        if (!selection.length) {
            form.reset();
            return;
        }
        Api.getAppointment(me.down('#provider').getValue(), selection[0].get('id')).then(function (a) {
            form.getForm().setValues({
                id: a.id,
                customerNumber: a.customerNumber,
                start: Api.formatUtc(a.start),
                end: Api.formatUtc(a.end),
                serviceName: a.serviceName + ' (' + a.serviceId + ')',
                repairService: a.repairServiceName + ' (' + a.repairServiceId + ')',
                source: a.source
            });
        }).catch(function (e) {
            Api.showError('Loading details failed', e);
        });
    }
});
