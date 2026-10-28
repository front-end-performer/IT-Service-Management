/** Provider selection, shared by all tabs. Loads GET /it-services once. */
Ext.define('Appointments.view.ProviderCombo', {
    extend: 'Ext.form.field.ComboBox',
    xtype: 'providercombo',

    fieldLabel: 'IT service provider',
    labelWidth: 140,
    width: 480,
    queryMode: 'local',
    editable: false,
    forceSelection: true,
    displayField: 'label',
    valueField: 'id',
    emptyText: 'Select a provider',

    initComponent: function () {
        var me = this;
        me.store = Ext.create('Ext.data.Store', {fields: ['id', 'name', 'capacity', 'timeZone', 'openingHours', 'label']});
        me.callParent();
        Appointments.Api.getProviders().then(function (providers) {
            me.store.loadData(providers.map(function (p) {
                return Ext.apply({label: p.name}, p);
            }));
        }).catch(function (e) {
            Appointments.Api.showError('Loading providers failed', e);
        });
    },

    /** Time zone of the selected provider; all times on screen are in this zone. */
    getTimeZone: function () {
        var record = this.getSelection();
        return record ? record.get('timeZone') : 'UTC';
    },

    /** ["2 appointments at a time", "Mon–Fri 08:00–18:00, Sat 09:00–13:00", "Times in Europe/Berlin"] */
    describeItems: function () {
        var record = this.getSelection();
        if (!record) {
            return [];
        }
        return [
            record.get('capacity') + (record.get('capacity') === 1 ? ' appointment' : ' appointments') + ' at a time',
            Appointments.Api.describeOpeningHours(record.get('openingHours')),
            'Times in ' + record.get('timeZone')
        ];
    },

    /** The same facts as chips (HTML). */
    describeChips: function () {
        return '<div class="app-chips">' + this.describeItems().map(function (item) {
            return '<span class="app-chip">' + Ext.String.htmlEncode(item) + '</span>';
        }).join('') + '</div>';
    }
});
