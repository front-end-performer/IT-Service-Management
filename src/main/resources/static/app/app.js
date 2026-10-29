// Labels as in the design: "Customer number", not "Customer number:".
Ext.define('Appointments.override.LabelSeparator', {override: 'Ext.form.field.Base', labelSeparator: ''});
Ext.define('Appointments.override.ContainerLabelSeparator', {override: 'Ext.form.FieldContainer', labelSeparator: ''});
// Fields never grow wider than this (inputs 360px, selects 250px, dates 128px), also when stretched by anchor or flex layouts.
Ext.define('Appointments.override.FieldMaxWidth', {override: 'Ext.form.field.Base', maxWidth: 360});
Ext.define('Appointments.override.ComboMaxWidth', {override: 'Ext.form.field.ComboBox', maxWidth: 250});
Ext.define('Appointments.override.DateMaxWidth', {override: 'Ext.form.field.Date', maxWidth: 128});

Ext.application({
    name: 'Appointments',

    launch: function () {
        var title = Appointments.i18n.t('app.title');
        document.title = title;
        var viewport = Ext.create('Ext.container.Viewport', {
            layout: 'fit',
            items: [{
                xtype: 'tabpanel',
                cls: 'app-shell',
                // Logo and name on the left of the tab bar, the tabs right next to them.
                tabBar: {
                    items: [{
                        xtype: 'component',
                        cls: 'app-brand',
                        margin: '0 32 0 0',
                        html: '<span class="app-logo"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" ' +
                            'stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
                            '<rect x="3" y="4" width="18" height="17" rx="2"></rect><path d="M3 9h18M8 2v4M16 2v4"></path>' +
                            '</svg></span>' + Ext.String.htmlEncode(title)
                    }]
                },
                defaults: {cls: 'app-page'},
                items: [
                    {xtype: 'bookingpanel'},
                    {xtype: 'appointmentspanel'},
                    {xtype: 'importpanel'}
                ]
            }]
        });

        // Ext adds the tabs in front of the tab bar's own items; the brand belongs before them.
        var tabBar = viewport.down('tabpanel').getTabBar();
        tabBar.insert(0, tabBar.down('component[cls=app-brand]'));

        // Keep the staff grid current after bookings and imports.
        var appointments = viewport.down('appointmentspanel');
        viewport.down('bookingpanel').on('appointmentbooked', appointments.reload, appointments);
        viewport.down('importpanel').on('appointmentsimported', appointments.reload, appointments);
    }
});
