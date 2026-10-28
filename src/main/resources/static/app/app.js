Ext.application({
    name: 'Appointments',

    launch: function () {
        document.title = Appointments.i18n.t('app.title');
        var viewport = Ext.create('Ext.container.Viewport', {
            layout: 'fit',
            items: [{
                xtype: 'tabpanel',
                title: Appointments.i18n.t('app.title'),
                items: [
                    {xtype: 'bookingpanel'},
                    {xtype: 'appointmentspanel'},
                    {xtype: 'importpanel'}
                ]
            }]
        });

        // Keep the staff grid current after bookings and imports.
        var appointments = viewport.down('appointmentspanel');
        viewport.down('bookingpanel').on('appointmentbooked', appointments.reload, appointments);
        viewport.down('importpanel').on('appointmentsimported', appointments.reload, appointments);
    }
});
