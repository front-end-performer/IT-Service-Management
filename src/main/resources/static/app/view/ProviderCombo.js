/** Provider selection, shared by all tabs. Loads GET /it-services once. */
Ext.define('Appointments.view.ProviderCombo', {
    extend: 'Ext.form.field.ComboBox',
    xtype: 'providercombo',

    fieldLabel: Appointments.i18n.t('provider.label'),
    labelWidth: 140,
    width: 480,
    queryMode: 'local',
    editable: false,
    forceSelection: true,
    displayField: 'label',
    valueField: 'id',
    emptyText: Appointments.i18n.t('provider.empty'),

    initComponent: function () {
        var me = this;
        me.store = Ext.create('Ext.data.Store', {fields: ['id', 'name', 'capacity', 'timeZone', 'openingHours', 'label']});
        me.callParent();
        // One request for all tabs' combos, so a failure is reported once, not once per tab.
        if (!me.self.providers) {
            me.self.providers = Appointments.Api.getProviders();
            me.self.providers.catch(function (e) {
                Appointments.Api.showError(Appointments.i18n.t('provider.loadFailed'), e);
            });
        }
        me.self.providers.then(function (providers) {
            me.store.loadData(providers.map(function (p) {
                return Ext.apply({label: p.name}, p);
            }));
        }, Ext.emptyFn); // already reported above
    },

    /** Time zone of the selected provider; all times on screen are in this zone. */
    getTimeZone: function () {
        var record = this.getSelection();
        return record ? record.get('timeZone') : 'UTC';
    },

    /** ["2 appointments at a time", "Mon–Fri 08:00–18:00, Sat 09:00–13:00", "Times in Europe/Berlin"] */
    describeItems: function () {
        var t = Appointments.i18n.t, record = this.getSelection();
        if (!record) {
            return [];
        }
        return [
            t(record.get('capacity') === 1 ? 'provider.capacity.one' : 'provider.capacity.other', record.get('capacity')),
            Appointments.Api.describeOpeningHours(record.get('openingHours')),
            t('provider.zone', record.get('timeZone'))
        ];
    },

    /** The same facts as chips (HTML). */
    describeChips: function () {
        return '<div class="app-chips">' + this.describeItems().map(function (item) {
            return '<span class="app-chip">' + Ext.String.htmlEncode(item) + '</span>';
        }).join('') + '</div>';
    }
});
