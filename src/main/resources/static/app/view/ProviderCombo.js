/** Provider selection, shared by all tabs. Loads GET /it-services once. */
Ext.define('Appointments.view.ProviderCombo', {
    extend: 'Ext.form.field.ComboBox',
    xtype: 'providercombo',

    fieldLabel: Appointments.i18n.t('provider.label'),
    labelWidth: 140,
    width: 250,
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
            // Start with the last used provider, else the first one, so no tab opens empty.
            var last = me.self.lastSelected();
            if (!me.getValue() && providers.length) {
                me.setValue(me.store.getById(last) ? last : providers[0].id);
            }
        }, Ext.emptyFn); // already reported above
        me.on('change', me.shareSelection, me);
    },

    statics: {
        STORAGE_KEY: 'appointments.provider',

        /** Provider chosen on any tab, also in an earlier visit (browser storage, when available). */
        lastSelected: function () {
            try {
                return this.selected || window.localStorage.getItem(this.STORAGE_KEY);
            } catch (e) {
                return null;
            }
        }
    },

    /** One provider for all tabs: a choice on one tab selects it on the others too. */
    shareSelection: function (combo, providerId) {
        if (!providerId) {
            return;
        }
        this.self.selected = providerId;
        try {
            window.localStorage.setItem(this.self.STORAGE_KEY, providerId);
        } catch (e) {
            // storage blocked: the choice is kept for this visit only
        }
        Ext.Array.each(Ext.ComponentQuery.query('providercombo'), function (other) {
            // Combos still loading pick up the shared choice once their providers arrive.
            if (other !== combo && other.getStore().getCount() && other.getValue() !== providerId) {
                other.setValue(providerId);
            }
        });
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
