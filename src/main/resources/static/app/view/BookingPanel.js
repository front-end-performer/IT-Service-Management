/**
 * Customer view: provider -> service -> period -> available start times -> book.
 * Times are shown and entered in the provider's local time zone. The API returns only start times
 * that are free for the whole duration of the service, so every tile shown can be booked.
 */
Ext.define('Appointments.view.BookingPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'bookingpanel',

    title: 'Book appointment',
    layout: {type: 'hbox', align: 'stretch'},
    bodyPadding: 16,

    START_HINT: '<div class="app-hint" style="padding: 20px">Choose provider, service and time window, then search.</div>',
    NOTHING_FOUND: '<div class="app-hint" style="padding: 20px">No free start times in this window. ' +
        'Try a longer window or another day.</div>',

    initComponent: function () {
        var me = this,
            tomorrow = Ext.Date.clearTime(Ext.Date.add(new Date(), Ext.Date.DAY, 1));

        me.serviceStore = Ext.create('Ext.data.Store', {fields: ['id', 'code', 'name', 'durationMinutes', 'label']});
        me.suggestionStore = Ext.create('Ext.data.Store', {
            fields: ['start', 'end', 'serviceId', 'repairServiceId', 'startLabel', 'endLabel', 'column', 'dayHeader', 'hourLabel']
        });

        me.items = [{
            xtype: 'form',
            itemId: 'searchForm',
            title: 'Find a slot',
            width: 380,
            margin: '0 16 0 0',
            bodyPadding: 20,
            scrollable: 'y',
            defaults: {labelAlign: 'top', anchor: '100%'},
            items: [{
                xtype: 'providercombo',
                itemId: 'provider',
                allowBlank: false,
                listeners: {change: me.onProviderChange, scope: me}
            }, {
                xtype: 'component',
                itemId: 'providerInfo',
                margin: '0 0 16 0',
                html: ''
            }, {
                xtype: 'combo',
                itemId: 'service',
                fieldLabel: 'Service',
                store: me.serviceStore,
                queryMode: 'local',
                editable: false,
                forceSelection: true,
                displayField: 'label',
                valueField: 'id',
                emptyText: 'Select a service',
                allowBlank: false,
                disabled: true,
                listeners: {change: me.onServiceChange, scope: me}
            }, {
                xtype: 'fieldcontainer',
                fieldLabel: 'From',
                layout: 'hbox',
                items: [
                    {xtype: 'datefield', itemId: 'fromDay', format: 'Y-m-d', value: tomorrow, allowBlank: false, flex: 1},
                    {xtype: 'timefield', itemId: 'fromTime', format: 'H:i', increment: 15, value: '08:00', allowBlank: false, width: 100, margin: '0 0 0 8'}
                ]
            }, {
                xtype: 'fieldcontainer',
                fieldLabel: 'To',
                layout: 'hbox',
                items: [
                    {xtype: 'datefield', itemId: 'toDay', format: 'Y-m-d', value: tomorrow, allowBlank: false, flex: 1},
                    {xtype: 'timefield', itemId: 'toTime', format: 'H:i', increment: 15, value: '18:00', allowBlank: false, width: 100, margin: '0 0 0 8'}
                ]
            }, {
                xtype: 'component',
                cls: 'app-hint',
                margin: '0 0 16 0',
                html: 'Up to 31 days. An appointment must end by the "To" time.'
            }, {
                xtype: 'button',
                itemId: 'searchButton',
                text: 'Find available slots',
                iconCls: 'x-fa fa-search',
                scale: 'medium',
                disabled: true, // enabled once a service is selected, see onServiceChange
                handler: me.search,
                scope: me
            }]
        }, {
            xtype: 'panel',
            itemId: 'slotPanel',
            title: 'Available slots',
            flex: 1,
            layout: 'fit',
            dockedItems: [{
                xtype: 'component',
                dock: 'top',
                itemId: 'slotInfo',
                cls: 'app-hint',
                padding: '12 20 4',
                html: 'Appointments start every 15 minutes. A time is listed only if the provider has a free place ' +
                    'for the whole duration of the service.'
            }, {
                xtype: 'component',
                dock: 'top',
                itemId: 'banner',
                padding: '8 20 0',
                hidden: true
            }, {
                xtype: 'toolbar',
                dock: 'bottom',
                padding: '10 20',
                items: [{
                    xtype: 'component',
                    itemId: 'selectedInfo',
                    flex: 1
                }, {
                    xtype: 'textfield',
                    itemId: 'customerNumber',
                    fieldLabel: 'Customer number',
                    labelWidth: 120,
                    width: 300,
                    maxLength: 64,
                    emptyText: 'e.g. 10001',
                    listeners: {change: me.updateSelection, scope: me}
                }, {
                    xtype: 'button',
                    itemId: 'bookButton',
                    ui: 'default', // the blue primary button, not the plain toolbar style
                    text: 'Book appointment',
                    iconCls: 'x-fa fa-check',
                    scale: 'medium',
                    disabled: true,
                    handler: me.book,
                    scope: me
                }]
            }],
            items: [{
                xtype: 'dataview',
                itemId: 'suggestions',
                store: me.suggestionStore,
                scrollable: true,
                // One row per hour, one column per quarter; headers are rendered between the tiles.
                tpl: [
                    '<div class="slot-grid"><tpl for=".">',
                    '<tpl if="dayHeader"><div class="slot-day">{dayHeader}</div></tpl>',
                    '<tpl if="hourLabel"><div class="slot-hour">{hourLabel}</div></tpl>',
                    '<div class="slot" style="grid-column: {column}" aria-label="{startLabel} to {endLabel}">',
                    '<span class="slot-start">{startLabel}</span><span class="slot-end">→ {endLabel}</span>',
                    '</div>',
                    '</tpl></div>'
                ],
                itemSelector: 'div.slot',
                overItemCls: 'slot-over',
                selectedItemCls: 'slot-selected',
                emptyText: me.START_HINT,
                deferEmptyText: false,
                listeners: {selectionchange: me.updateSelection, scope: me}
            }]
        }];
        me.callParent();
        me.updateSelection();
    },

    onProviderChange: function (combo, providerId) {
        var me = this, serviceCombo = me.down('#service');
        me.down('#providerInfo').setHtml(combo.describeChips());
        me.down('#searchButton').disable();
        serviceCombo.clearValue();
        me.serviceStore.removeAll();
        me.clearSlots();
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

    /** Services are loaded per provider, so a selected service implies a selected provider. */
    onServiceChange: function (combo, serviceId) {
        this.down('#searchButton').setDisabled(!serviceId);
        this.clearSlots();
    },

    /** Provider or service changed: the listed start times no longer apply. */
    clearSlots: function () {
        this.down('#suggestions').emptyText = this.START_HINT;
        this.suggestionStore.removeAll();
        this.down('#slotPanel').setTitle('Available slots');
        this.down('#banner').hide();
        this.updateSelection();
    },

    search: function () {
        this.down('#banner').hide();
        this.loadSuggestions();
    },

    /** Loads the start times for the form's values; also called after every booking attempt. */
    loadSuggestions: function () {
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
                me.showSlots(suggestions, zone);
            })
            .catch(function (e) {
                me.clearSlots();
                Api.showBanner(me.down('#banner'), 'error',
                    '<b>Search failed</b>' + Ext.String.htmlEncode(Api.errorText(e, zone)));
            })
            .finally(function () {
                me.setLoading(false);
            });
    },

    showSlots: function (suggestions, zone) {
        var me = this, Api = Appointments.Api, lastDay, lastHour,
            service = me.down('#service').getSelection();
        me.down('#suggestions').emptyText = me.NOTHING_FOUND;
        me.suggestionStore.loadData(suggestions.map(function (s) {
            var p = Api.zonedParts(new Date(s.start), zone),
                day = p.year + p.month + p.day,
                tile = Ext.apply({
                    startLabel: p.hour + ':' + p.minute,
                    endLabel: Api.formatClock(s.end, zone),
                    column: p.minute / 15 + 2,
                    dayHeader: day !== lastDay
                        ? Api.formatDate(s.start, zone, {weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'}) : '',
                    hourLabel: day !== lastDay || p.hour !== lastHour ? p.hour : ''
                }, s);
            lastDay = day;
            lastHour = p.hour;
            return tile;
        }));
        me.down('#slotPanel').setTitle('Available slots · ' + suggestions.length +
            (suggestions.length === 1 ? ' start time' : ' start times'));
        me.down('#slotInfo').setHtml(Ext.String.htmlEncode('Appointments start every 15 minutes. A time is listed ' +
            'only if the provider has a free place for the whole ' + service.get('durationMinutes') + ' min of ' +
            service.get('name') + '.'));
        me.updateSelection();
    },

    /** "Thu 29 Oct · 10:00–12:00 · LAP" */
    describeSlot: function (suggestion) {
        return Appointments.Api.formatDate(suggestion.get('start'), this.down('#provider').getTimeZone(),
            {weekday: 'short', day: 'numeric', month: 'short'}) + ' · ' + suggestion.get('startLabel') + '–' +
            suggestion.get('endLabel') + ' · ' + suggestion.get('repairServiceId');
    },

    updateSelection: function () {
        var suggestion = this.down('#suggestions').getSelection()[0],
            customer = Ext.String.trim(this.down('#customerNumber').getValue() || '');
        this.down('#selectedInfo').setHtml(suggestion
            ? '<b>' + Ext.String.htmlEncode(this.describeSlot(suggestion)) + '</b>' +
            '<div class="app-hint">Availability is checked again when you book.</div>'
            : '<span class="app-hint">No slot selected</span>');
        this.down('#bookButton').setDisabled(!suggestion || !customer);
    },

    book: function () {
        var me = this, Api = Appointments.Api,
            suggestion = me.down('#suggestions').getSelection()[0],
            providerId = suggestion.get('serviceId'),
            zone = me.down('#provider').getTimeZone(),
            when = Ext.String.htmlEncode(me.describeSlot(suggestion)),
            banner = me.down('#banner');
        me.setLoading('Booking…');
        Api.book(providerId, {
            customerNumber: Ext.String.trim(me.down('#customerNumber').getValue()),
            start: suggestion.get('start'),
            repairServiceId: suggestion.get('repairServiceId')
        }).then(function (appointment) {
            Api.showBanner(banner, 'success', Ext.String.format(
                '<b>Appointment booked</b>{0} for customer {1}. Appointment id: {2}',
                when, Ext.String.htmlEncode(appointment.customerNumber), Ext.String.htmlEncode(appointment.id)));
            me.fireEvent('appointmentbooked', providerId);
        }).catch(function (e) {
            Api.showBanner(banner, e.status === 503 ? 'warn' : 'error', me.describeBookingError(e, when, zone));
        }).finally(function () {
            // Show fresh availability: the booked or taken time is gone from the list.
            me.setLoading(false);
            me.loadSuggestions();
        });
    },

    describeBookingError: function (e, when, zone) {
        if (e.status === 409 && /already has/.test(e.message)) {
            // Both conflicts are 409; the duplicate one says "Customer … already has … at …".
            return '<b>Already booked</b>This customer already has ' + when + '. Nothing new was booked.';
        }
        if (e.status === 409) {
            return '<b>This slot was just taken</b>' + when + ' was booked by someone else a moment ago. ' +
                'Nothing was booked for you. The list below is up to date, please pick another time.';
        }
        if (e.status === 503) {
            return '<b>The provider is busy</b>Nothing was booked. Please try again in a moment.';
        }
        return '<b>Booking failed</b>' + Ext.String.htmlEncode(Appointments.Api.errorText(e, zone));
    }
});
