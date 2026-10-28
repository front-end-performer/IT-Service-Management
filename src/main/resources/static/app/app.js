Ext.application({
    name: 'Appointments',

    launch: function () {
        var viewport = Ext.create('Ext.container.Viewport', {
            layout: 'fit',
            items: [{
                xtype: 'tabpanel',
                title: 'IT Service Appointments',
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
