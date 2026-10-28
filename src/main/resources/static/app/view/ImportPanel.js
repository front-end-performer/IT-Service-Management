/**
 * Legacy CSV import. The target provider is chosen explicitly – never derived from the file name.
 */
Ext.define('Appointments.view.ImportPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'importpanel',

    title: Appointments.i18n.t('import.title'),
    layout: {type: 'hbox', align: 'stretch'},
    bodyPadding: 16,

    /** RejectionReason -> text shown in the grid. */
    REASONS: ['MALFORMED_ROW', 'MISSING_FIELD', 'INVALID_CUSTOMER_NUMBER', 'INVALID_DATE', 'OFF_GRID',
        'OUTSIDE_OPENING_HOURS', 'UNKNOWN_SERVICE_CODE', 'CAPACITY_EXCEEDED', 'DUPLICATE'
    ].reduce(function (texts, reason) {
        texts[reason] = Appointments.i18n.t('import.reason.' + reason);
        return texts;
    }, {}),

    /** Banner title for an import that did not complete. */
    STATUSES: {
        RUNNING: Appointments.i18n.t('import.status.RUNNING'),
        FAILED: Appointments.i18n.t('import.status.FAILED')
    },

    initComponent: function () {
        var me = this, t = Appointments.i18n.t;
        me.rejectionStore = Ext.create('Ext.data.Store', {fields: ['lineNumber', 'rawLine', 'reason', 'message']});

        me.items = [{
            xtype: 'form',
            itemId: 'uploadForm',
            title: t('import.formTitle'),
            width: 420,
            margin: '0 16 0 0',
            bodyPadding: 20,
            scrollable: 'y',
            defaults: {labelAlign: 'top', anchor: '100%'},
            items: [{
                xtype: 'providercombo',
                itemId: 'provider',
                fieldLabel: t('import.provider'),
                allowBlank: false
            }, {
                xtype: 'filefield',
                itemId: 'file',
                fieldLabel: t('import.file'),
                allowBlank: false,
                buttonText: t('import.choose'),
                listeners: {
                    afterrender: function (field) {
                        field.fileInputEl.set({accept: '.csv,text/csv'});
                    }
                }
            }, {
                xtype: 'component',
                cls: 'app-hint',
                margin: '0 0 16 0',
                html: t('import.format')
            }, {
                xtype: 'component',
                cls: 'app-hint',
                margin: '0 0 16 0',
                html: t('import.checks')
            }, {
                xtype: 'button',
                text: t('import.import'),
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
                title: t('import.lastImport'),
                bodyPadding: '12 16',
                margin: '0 0 16 0',
                html: '<span class="app-hint">' + t('import.none') + '</span>'
            }, {
                xtype: 'grid',
                title: t('import.rejectedRows'),
                flex: 1,
                store: me.rejectionStore,
                emptyText: t('import.noRejected'),
                columns: [
                    {text: t('import.line'), dataIndex: 'lineNumber', width: 70},
                    {text: t('import.reason'), dataIndex: 'reason', width: 200, renderer: me.renderReason, scope: me},
                    {text: t('import.row'), dataIndex: 'rawLine', width: 260, renderer: Ext.String.htmlEncode},
                    {text: t('import.message'), dataIndex: 'message', flex: 1, renderer: Ext.String.htmlEncode}
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
        var me = this, Api = Appointments.Api, t = Appointments.i18n.t, summary = me.down('#summary');
        if (!me.down('#uploadForm').isValid()) {
            return;
        }
        var providerId = me.down('#provider').getValue(),
            file = me.down('#file').fileInputEl.dom.files[0];
        me.setLoading(t('import.importing'));
        Api.importCsv(providerId, file).then(function (report) {
            summary.setTitle(t('import.lastImportInto', Ext.String.htmlEncode(report.fileName || ''), report.serviceId));
            summary.update('<div class="app-counters">' +
                t('import.counters', report.totalRows, report.imported, report.duplicates, report.rejected) + '</div>' +
                (report.status === 'COMPLETED' ? '' : '<div class="app-banner app-banner-error" style="margin-top: 12px">' +
                    '<b>' + (me.STATUSES[report.status] || report.status) + '</b>' +
                    Ext.String.htmlEncode(report.failureMessage || '') + '</div>'));
            me.rejectionStore.loadData(report.rejections);
            me.fireEvent('appointmentsimported', providerId);
        }).catch(function (e) {
            // e.g. 500 "Import aborted": rows imported so far stay; importing the file again is safe.
            summary.setTitle(t('import.lastImportFailed'));
            summary.update('<div class="app-banner app-banner-error"><b>' + t('import.failed') + '</b>' +
                Ext.String.htmlEncode(e.message) + '</div>');
            me.rejectionStore.removeAll();
            me.fireEvent('appointmentsimported', providerId);
        }).finally(function () {
            me.setLoading(false);
        });
    }
});
