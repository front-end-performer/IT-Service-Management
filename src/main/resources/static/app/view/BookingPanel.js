/**
 * Customer view: provider -> service -> period -> suggestions -> book.
 * Times are shown and entered in the provider's local time zone.
 */
Ext.define('Appointments.view.BookingPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'bookingpanel',

    title: 'Book appointment',
    layout: {type: 'vbox', align: 'stretch'},
    bodyPadding: 10,

    initComponent: function () {
        var me = this,
            tomorrow = Ext.Date.clearTime(Ext.Date.add(new Date(), Ext.Date.DAY, 1));

        me.serviceStore = Ext.create('Ext.data.Store', {fields: ['id', 'code', 'name', 'durationMinutes', 'label']});
        me.suggestionStore = Ext.create('Ext.data.Store', {fields: ['start', 'end', 'serviceId', 'repairServiceId']});

        me.items = [{
            xtype: 'form',
            itemId: 'searchForm',
            border: false,
            layout: {type: 'vbox'},
            defaults: {labelWidth: 140},
            items: [{
                xtype: 'providercombo',
                itemId: 'provider',
                allowBlank: false,
                listeners: {change: me.onProviderChange, scope: me}
            }, {
                xtype: 'component',
                itemId: 'providerInfo',
                margin: '0 0 8 145',
                style: {color: '#666'},
                html: ''
            }, {
                xtype: 'combo',
                itemId: 'service',
                fieldLabel: 'Service',
                width: 480,
                store: me.serviceStore,
                queryMode: 'local',
                editable: false,
                forceSelection: true,
                displayField: 'label',
                valueField: 'id',
                allowBlank: false,
                disabled: true
            }, {
                xtype: 'fieldcontainer',
                fieldLabel: 'From',
                layout: 'hbox',
                items: [
                    {xtype: 'datefield', itemId: 'fromDay', format: 'Y-m-d', value: tomorrow, allowBlank: false, width: 140},
                    {xtype: 'timefield', itemId: 'fromTime', format: 'H:i', increment: 15, value: '08:00', allowBlank: false, width: 100, margin: '0 0 0 5'}
                ]
            }, {
                xtype: 'fieldcontainer',
                fieldLabel: 'To',
                layout: 'hbox',
                items: [
                    {xtype: 'datefield', itemId: 'toDay', format: 'Y-m-d', value: tomorrow, allowBlank: false, width: 140},
                    {xtype: 'timefield', itemId: 'toTime', format: 'H:i', increment: 15, value: '18:00', allowBlank: false, width: 100, margin: '0 0 0 5'}
                ]
            }, {
                xtype: 'button',
                text: 'Find available appointments',
                iconCls: 'x-fa fa-search',
                margin: '0 0 0 145',
                handler: me.findSuggestions,
                scope: me
            }]
        }, {
            xtype: 'grid',
            itemId: 'suggestions',
            title: 'Available appointments',
            flex: 1,
            margin: '10 0',
            store: me.suggestionStore,
            emptyText: 'No suggestions – choose provider, service and period, then search.',
            columns: [
                {text: 'Start', dataIndex: 'start', flex: 1, renderer: me.renderTime, scope: me},
                {text: 'End', dataIndex: 'end', flex: 1, renderer: me.renderTime, scope: me},
                {text: 'Service', dataIndex: 'repairServiceId', width: 120}
            ],
            listeners: {selectionchange: me.updateBookButton, scope: me}
        }, {
            xtype: 'container',
            layout: 'hbox',
            items: [{
                xtype: 'textfield',
                itemId: 'customerNumber',
                fieldLabel: 'Customer number',
                labelWidth: 140,
                width: 340,
                maxLength: 64,
                listeners: {change: me.updateBookButton, scope: me}
            }, {
                xtype: 'button',
                itemId: 'bookButton',
                text: 'Book selected appointment',
                iconCls: 'x-fa fa-check',
                margin: '0 0 0 10',
                disabled: true,
                handler: me.book,
                scope: me
            }]
        }];
        me.callParent();
    },

    renderTime: function (iso) {
        return Appointments.Api.formatInZone(iso, this.down('#provider').getTimeZone());
    },

    onProviderChange: function (combo, providerId) {
        var me = this, serviceCombo = me.down('#service');
        me.down('#providerInfo').setHtml(Ext.String.htmlEncode(combo.describeSelection()));
        serviceCombo.clearValue();
        me.serviceStore.removeAll();
        me.suggestionStore.removeAll();
        if (!providerId) {
            return;
        }
        Appointments.Api.getServices(providerId).then(function (services) {
            me.serviceStore.loadData(services.map(function (s) {
                return Ext.apply({label: s.code + ' – ' + s.name + ' (' + s.durationMinutes + ' min)'}, s);
            }));
            serviceCombo.enable();
        }).catch(function (e) {
            Appointments.Api.showError('Loading services failed', e);
        });
    },

    findSuggestions: function () {
        var me = this, Api = Appointments.Api;
        if (!me.down('#searchForm').isValid()) {
            return;
        }
        var zone = me.down('#provider').getTimeZone(),
            from = Api.toInstant(me.down('#fromDay').getValue(), me.down('#fromTime').getValue(), zone),
            to = Api.toInstant(me.down('#toDay').getValue(), me.down('#toTime').getValue(), zone);
        me.setLoading('Searching…');
        Api.getSuggestions(me.down('#provider').getValue(), me.down('#service').getValue(), from, to)
            .then(function (suggestions) {
                me.suggestionStore.loadData(suggestions);
            })
            .catch(function (e) {
                me.suggestionStore.removeAll();
                Api.showError('Search failed', e);
            })
            .finally(function () {
                me.setLoading(false);
            });
    },

    updateBookButton: function () {
        var selected = this.down('#suggestions').getSelection().length > 0,
            customer = Ext.String.trim(this.down('#customerNumber').getValue() || '');
        this.down('#bookButton').setDisabled(!selected || !customer);
    },

    book: function () {
        var me = this, Api = Appointments.Api,
            suggestion = me.down('#suggestions').getSelection()[0],
            providerId = suggestion.get('serviceId'),
            zone = me.down('#provider').getTimeZone();
        Api.book(providerId, {
            customerNumber: Ext.String.trim(me.down('#customerNumber').getValue()),
            start: suggestion.get('start'),
            repairServiceId: suggestion.get('repairServiceId')
        }).then(function (appointment) {
            Ext.Msg.alert('Appointment booked', Ext.String.format(
                'Booked {0} for customer {1}<br>{2} – {3} ({4})<br>Appointment id: {5}',
                Ext.String.htmlEncode(appointment.repairServiceName),
                Ext.String.htmlEncode(appointment.customerNumber),
                Api.formatInZone(appointment.start, zone), Api.formatInZone(appointment.end, zone).substring(11),
                zone, appointment.id));
            me.fireEvent('appointmentbooked', providerId);
        }).catch(function (e) {
            // HTTP 409: someone else took the last capacity since the search – show fresh availability.
            Api.showError('Booking failed', e);
        }).finally(function () {
            me.findSuggestions();
        });
    }
});
