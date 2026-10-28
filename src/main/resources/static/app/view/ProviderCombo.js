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
        me.store = Ext.create('Ext.data.Store', {fields: ['id', 'name', 'capacity', 'label']});
        me.callParent();
        Appointments.Api.getProviders().then(function (providers) {
            me.store.loadData(providers.map(function (p) {
                return Ext.apply({label: p.name + ' (capacity ' + p.capacity + ')'}, p);
            }));
        }).catch(function (e) {
            Appointments.Api.showError('Loading providers failed', e);
        });
    }
});
