/**
 * Legacy CSV import. The target provider is chosen explicitly – never derived from the file name.
 */
Ext.define('Appointments.view.ImportPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'importpanel',

    title: 'Import CSV',
    layout: {type: 'hbox', align: 'stretch'},
    bodyPadding: 16,

    /** RejectionReason -> text shown in the grid. */
    REASONS: {
        MALFORMED_ROW: 'Wrong column count',
        MISSING_FIELD: 'Missing field',
        INVALID_CUSTOMER_NUMBER: 'Invalid customer number',
        INVALID_DATE: 'Invalid date',
        OFF_GRID: 'Not on 15-min step',
        OUTSIDE_OPENING_HOURS: 'Outside opening hours',
        UNKNOWN_SERVICE_CODE: 'Unknown service',
        CAPACITY_EXCEEDED: 'Capacity exceeded',
        DUPLICATE: 'Duplicate, skipped'
    },

    initComponent: function () {
        var me = this;
        me.rejectionStore = Ext.create('Ext.data.Store', {fields: ['lineNumber', 'rawLine', 'reason', 'message']});

        me.items = [{
            xtype: 'form',
            itemId: 'uploadForm',
            title: 'Import appointments',
            width: 420,
            margin: '0 16 0 0',
            bodyPadding: 20,
            scrollable: 'y',
            defaults: {labelAlign: 'top', anchor: '100%'},
            items: [{
                xtype: 'providercombo',
                itemId: 'provider',
                fieldLabel: 'Import into provider',
                allowBlank: false
            }, {
                xtype: 'filefield',
                itemId: 'file',
                fieldLabel: 'CSV file',
                allowBlank: false,
                buttonText: 'Choose…',
                listeners: {
                    afterrender: function (field) {
                        field.fileInputEl.set({accept: '.csv,text/csv'});
                    }
                }
            }, {
                xtype: 'component',
                cls: 'app-hint',
                margin: '0 0 16 0',
                html: '<b>Expected format</b><br><code>KdnNr,APP_DATE,SERVICE</code><br>' +
                    '<code>10001,2026-11-02T08:00Z,DIA</code><br>' +
                    'APP_DATE is in UTC; the app shows it in the provider\'s time zone.'
            }, {
                xtype: 'component',
                cls: 'app-hint',
                margin: '0 0 16 0',
                html: '<b>Each row is checked on its own</b><ul class="app-checklist">' +
                    '<li>3 columns, customer number not empty (max. 64 characters)</li>' +
                    '<li>Valid timestamp on a 15-minute step</li>' +
                    '<li>Service offered by this provider</li>' +
                    '<li>Within opening hours</li>' +
                    '<li>Capacity holds for the whole duration</li>' +
                    '<li>Not already imported (skipped as a duplicate)</li>' +
                    '</ul>Valid rows are imported; the others are listed with a reason.'
            }, {
                xtype: 'button',
                text: 'Import',
                iconCls: 'x-fa fa-upload',
                scale: 'medium',
                formBind: true, // enabled once provider and file are both set
                disabled: true,
                handler: me.upload,
                scope: me
            }]
        }, {
            xtype: 'container',
            flex: 1,
            layout: {type: 'vbox', align: 'stretch'},
            items: [{
                xtype: 'panel',
                itemId: 'summary',
                title: 'Last import',
                bodyPadding: '12 16',
                margin: '0 0 16 0',
                html: '<span class="app-hint">No file imported yet.</span>'
            }, {
                xtype: 'grid',
                title: 'Rows not imported',
                flex: 1,
                store: me.rejectionStore,
                emptyText: 'No rejected rows.',
                columns: [
                    {text: 'Line', dataIndex: 'lineNumber', width: 70},
                    {text: 'Reason', dataIndex: 'reason', width: 200, renderer: me.renderReason, scope: me},
                    {text: 'Row', dataIndex: 'rawLine', width: 260, renderer: Ext.String.htmlEncode},
                    {text: 'Message', dataIndex: 'message', flex: 1, renderer: Ext.String.htmlEncode}
                ]
            }]
        }];
        me.callParent();
    },

    renderReason: function (reason) {
        return '<span class="app-badge ' + (reason === 'DUPLICATE' ? 'app-badge-muted' : 'app-badge-warn') + '">' +
            Ext.String.htmlEncode(this.REASONS[reason] || reason) + '</span>';
    },

    upload: function () {
        var me = this, Api = Appointments.Api, summary = me.down('#summary');
        if (!me.down('#uploadForm').isValid()) {
            return;
        }
        var providerId = me.down('#provider').getValue(),
            file = me.down('#file').fileInputEl.dom.files[0];
        me.setLoading('Importing…');
        Api.importCsv(providerId, file).then(function (report) {
            summary.setTitle('Last import · ' + Ext.String.htmlEncode(report.fileName || '') + ' into ' + report.serviceId);
            summary.update(Ext.String.format(
                '<div class="app-counters"><span>{0} rows</span><span class="ok"><b>{1}</b> imported</span>' +
                '<span>{2} duplicates skipped</span><span class="bad"><b>{3}</b> rejected</span></div>{4}',
                report.totalRows, report.imported, report.duplicates, report.rejected,
                report.status === 'COMPLETED' ? '' : '<div class="app-banner app-banner-error" style="margin-top: 12px">' +
                    '<b>Import ' + report.status.toLowerCase() + '</b>' + Ext.String.htmlEncode(report.failureMessage || '') + '</div>'));
            me.rejectionStore.loadData(report.rejections);
            me.fireEvent('appointmentsimported', providerId);
        }).catch(function (e) {
            // e.g. 500 "Import aborted": rows imported so far stay; importing the file again is safe.
            summary.setTitle('Last import · failed');
            summary.update('<div class="app-banner app-banner-error"><b>Import failed</b>' +
                Ext.String.htmlEncode(e.message) + '</div>');
            me.rejectionStore.removeAll();
            me.fireEvent('appointmentsimported', providerId);
        }).finally(function () {
            me.setLoading(false);
        });
    }
});
