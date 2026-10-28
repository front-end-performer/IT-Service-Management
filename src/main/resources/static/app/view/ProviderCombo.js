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

    initComponent: function () {
        var me = this;
        me.store = Ext.create('Ext.data.Store', {fields: ['id', 'name', 'capacity', 'timeZone', 'openingHours', 'label']});
        me.callParent();
        Appointments.Api.getProviders().then(function (providers) {
            me.store.loadData(providers.map(function (p) {
                return Ext.apply({label: p.name + ' (capacity ' + p.capacity + ')'}, p);
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

    /** "Times in Europe/Berlin · Opening hours: Mon–Fri 08:00–18:00, Sat 09:00–13:00" */
    describeSelection: function () {
        var record = this.getSelection();
        if (!record) {
            return '';
        }
        return 'Times in ' + record.get('timeZone') + ' · Opening hours: ' +
            Appointments.Api.describeOpeningHours(record.get('openingHours'));
    }
});
