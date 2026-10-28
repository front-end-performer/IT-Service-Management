/**
 * Legacy CSV import. The target provider is chosen explicitly – never derived from the file name.
 */
Ext.define('Appointments.view.ImportPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'importpanel',

    title: 'Import CSV',
    layout: {type: 'vbox', align: 'stretch'},
    bodyPadding: 10,

    initComponent: function () {
        var me = this;
        me.rejectionStore = Ext.create('Ext.data.Store', {fields: ['lineNumber', 'rawLine', 'reason', 'message']});

        me.items = [{
            xtype: 'form',
            itemId: 'uploadForm',
            border: false,
            items: [{
                xtype: 'providercombo',
                itemId: 'provider',
                fieldLabel: 'Import into provider',
                allowBlank: false
            }, {
                xtype: 'filefield',
                itemId: 'file',
                fieldLabel: 'CSV file',
                labelWidth: 140,
                width: 480,
                allowBlank: false,
                buttonText: 'Choose…',
                listeners: {
                    afterrender: function (field) {
                        field.fileInputEl.set({accept: '.csv,text/csv'});
                    }
                }
            }, {
                xtype: 'button',
                text: 'Import',
                iconCls: 'x-fa fa-upload',
                margin: '0 0 0 145',
                handler: me.upload,
                scope: me
            }]
        }, {
            xtype: 'component',
            itemId: 'summary',
            margin: '15 0 5 0',
            html: 'Expected format: <code>KdnNr,APP_DATE,SERVICE</code>, e.g. <code>10001,2026-11-02T08:00Z,DIA</code>'
        }, {
            xtype: 'grid',
            title: 'Rows not imported',
            flex: 1,
            store: me.rejectionStore,
            emptyText: 'No rejected rows.',
            columns: [
                {text: 'Line', dataIndex: 'lineNumber', width: 70},
                {text: 'Reason', dataIndex: 'reason', width: 200},
                {text: 'Row', dataIndex: 'rawLine', width: 260, renderer: Ext.String.htmlEncode},
                {text: 'Message', dataIndex: 'message', flex: 1, renderer: Ext.String.htmlEncode}
            ]
        }];
        me.callParent();
    },

    upload: function () {
        var me = this, Api = Appointments.Api;
        if (!me.down('#uploadForm').isValid()) {
            return;
        }
        var providerId = me.down('#provider').getValue(),
            file = me.down('#file').fileInputEl.dom.files[0];
        me.setLoading('Importing…');
        Api.importCsv(providerId, file).then(function (report) {
            me.down('#summary').setHtml(Ext.String.format(
                '<b>{0}</b> into <b>{1}</b>: {2} rows – <b>{3} imported</b>, {4} duplicates skipped, <b>{5} rejected</b>',
                Ext.String.htmlEncode(report.fileName || ''), report.serviceId, report.totalRows,
                report.imported, report.duplicates, report.rejected));
            me.rejectionStore.loadData(report.rejections);
            me.fireEvent('appointmentsimported', providerId);
        }).catch(function (e) {
            Api.showError('Import failed', e);
        }).finally(function () {
            me.setLoading(false);
        });
    }
});
