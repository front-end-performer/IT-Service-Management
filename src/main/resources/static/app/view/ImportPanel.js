/**
 * Legacy CSV import. The target provider is chosen explicitly – never derived from the file name.
 */
Ext.define('Appointments.view.ImportPanel', {
    extend: 'Ext.panel.Panel',
    xtype: 'importpanel',

    title: Appointments.i18n.t('import.title'),
    layout: {type: 'hbox', align: 'stretch'},
    bodyPadding: '32 24 24',

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
            // The form card keeps its natural height; the column scrolls on short screens.
            xtype: 'container',
            width: 420,
            margin: '0 24 0 0',
            scrollable: 'y',
            layout: {type: 'vbox', align: 'stretch'},
            items: [{
                xtype: 'form',
                itemId: 'uploadForm',
                cls: 'app-card',
                border: false,
                margin: 1, // room for the card's outline
                title: t('import.formTitle'),
                bodyPadding: '18 24 24',
                defaults: {anchor: '100%', margin: '0 0 18 0'},
                items: [{
                    xtype: 'providercombo',
                    itemId: 'provider',
                    fieldLabel: t('import.provider'),
                    labelAlign: 'top',
                    width: null, // full panel width: neither the combo's 250px nor the app-wide combo cap
                    maxWidth: null,
                    allowBlank: false,
                    listeners: {change: me.onProviderChange, scope: me}
                }, {
                    // Drop zone with a hidden file input; the chosen file is shown below it.
                    xtype: 'component',
                    itemId: 'file',
                    html: '<span class="app-label">' + t('import.file') + '</span>' +
                        '<label class="app-drop-zone">' +
                        '<svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#1F5FBF" stroke-width="1.8" ' +
                        'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
                        '<path d="M12 15V4M7 9l5-5 5 5M4 15v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4"></path></svg>' +
                        '<span>' + t('import.drop') + '</span>' +
                        '<span class="app-hint">' + t('import.fileTypes') + '</span>' +
                        '<span class="app-drop-error" role="alert"></span>' +
                        '<input class="app-drop-input" type="file" accept=".csv,text/csv">' +
                        '</label>' +
                        '<div class="app-drop-file" hidden>' +
                        '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#4A5565" stroke-width="1.8" ' +
                        'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
                        '<path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z"></path><path d="M14 3v5h5"></path></svg>' +
                        '<span></span>' +
                        '<button type="button" aria-label="' + t('import.removeFile') + '">' +
                        '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" ' +
                        'stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18"></path></svg></button>' +
                        '</div>',
                    listeners: {afterrender: me.initDropZone, scope: me}
                }, {
                    // Warns when the file has service codes the selected provider does not offer, see checkFile.
                    xtype: 'component',
                    itemId: 'fileCheck',
                    hidden: true
                }, {
                    xtype: 'component',
                    cls: 'app-format',
                    html: t('import.format')
                }, {
                    xtype: 'component',
                    cls: 'app-checks',
                    html: t('import.checks')
                }, {
                    xtype: 'button',
                    itemId: 'importButton',
                    cls: 'app-btn-primary app-btn-tall',
                    text: t('import.import'),
                    iconCls: 'x-fa fa-upload',
                    scale: 'medium',
                    margin: 0,
                    disabled: true, // enabled once provider and file are both set, see updateImportButton
                    handler: me.upload,
                    scope: me
                }]
            }]
        }, {
            xtype: 'container',
            flex: 1,
            layout: {type: 'vbox', align: 'stretch'},
            items: [{
                xtype: 'component',
                itemId: 'summary',
                cls: 'app-card',
                margin: '1 1 17 1', // 16px gap plus room for the card's outline
                padding: '16 20',
                hidden: true // shown after the first import, see showSummary
            }, {
                xtype: 'grid',
                itemId: 'rejections',
                cls: 'app-card app-card-small app-grid-centered-empty app-quiet-mask', // importing shows a spinner here only
                border: false,
                margin: 1, // room for the card's outline
                title: t('import.rejectedRows'),
                flex: 1,
                store: me.rejectionStore,
                emptyText: me.emptyHtml('import.noImports'),
                listeners: {afterrender: me.updateRejections, scope: me}, // the title bar exists only once rendered
                columns: [
                    {text: t('import.line'), dataIndex: 'lineNumber', width: 72, tdCls: 'app-cell-muted'},
                    {text: t('import.reason'), dataIndex: 'reason', width: 220, renderer: me.renderReason, scope: me},
                    {text: t('import.row'), dataIndex: 'rawLine', width: 280, renderer: function (v) {
                        return '<span class="app-code">' + Ext.String.htmlEncode(v) + '</span>';
                    }},
                    {text: t('import.message'), dataIndex: 'message', flex: 1, cellWrap: true, tdCls: 'app-cell-muted',
                        renderer: Ext.String.htmlEncode}
                ]
            }]
        }];
        me.callParent();
    },

    emptyHtml: function (key) {
        return '<div class="app-empty">' + Appointments.i18n.t(key) + '</div>';
    },

    /** Without rejected rows the card shows only the empty text: no title, no column headers. */
    updateRejections: function () {
        var grid = this.down('#rejections'), empty = !this.rejectionStore.getCount();
        grid.toggleCls('app-grid-is-empty', empty);
        grid.getHeader().setHidden(empty);
        grid.headerCt.setHidden(empty);
    },

    /** Wires the hidden input, drag and drop and the remove button of the file component. */
    initDropZone: function (component) {
        var me = this, el = component.el,
            zone = el.down('.app-drop-zone'),
            input = el.down('.app-drop-input');
        input.on('change', function () {
            me.setFile(input.dom.files[0] || null);
        });
        zone.on('dragover', function (e) {
            e.preventDefault();
            zone.addCls('app-drop-over');
        });
        zone.on('dragleave', function () {
            zone.removeCls('app-drop-over');
        });
        zone.on('drop', function (e) {
            e.preventDefault();
            zone.removeCls('app-drop-over');
            me.setFile(e.browserEvent.dataTransfer.files[0] || null);
        });
        el.down('.app-drop-file button').on('click', function () {
            input.dom.value = '';
            me.setFile(null);
        });
    },

    /** The file to import, or null; shows its name instead of the drop zone. Other than .csv files are refused. */
    setFile: function (file) {
        var component = this.down('#file'), el = component.el;
        var refused = file && !/\.csv$/i.test(file.name);
        // A refused file is named inside the drop zone, which stays open for another try.
        el.down('.app-drop-error').setHtml(refused ? Appointments.i18n.t('import.notCsv', Ext.String.htmlEncode(file.name)) : '');
        el.down('.app-drop-zone').toggleCls('app-drop-invalid', !!refused);
        if (refused) {
            el.down('.app-drop-input').dom.value = '';
            file = null;
        }
        this.file = file;
        el.down('.app-drop-file span').setHtml(file ? Ext.String.htmlEncode(file.name) : '');
        el.down('.app-drop-file').dom.hidden = !file;
        el.down('.app-drop-zone').setDisplayed(!file);
        component.updateLayout();
        this.updateImportButton();
        this.checkFile();
    },

    onProviderChange: function () {
        this.updateImportButton();
        this.checkFile();
    },

    /**
     * Before importing: does the file fit the selected provider? The file has no provider column, so a wrong choice
     * shows only in its service codes. Codes the provider does not offer are named, with the provider that offers all
     * of them, if any. Only a warning: importing stays possible and the server checks every row anyway. A file with
     * only codes both providers offer (DIA) cannot be told apart, see ADR 0005.
     */
    checkFile: function () {
        var me = this, Api = Appointments.Api, t = Appointments.i18n.t, combo = me.down('#provider'),
            file = me.file, providerId = combo.getValue(), providers = combo.getStore().getRange(),
            check = me.fileCheck = (me.fileCheck || 0) + 1; // a newer file or provider wins
        me.showFileWarning(null);
        if (!file || !providerId) {
            return;
        }
        Promise.all([file.text(), Promise.all(providers.map(function (p) {
            return Api.getServices(p.getId());
        }))]).then(function (results) {
            if (check !== me.fileCheck) {
                return;
            }
            var codes = me.serviceCodes(results[0]), offered = {};
            providers.forEach(function (p, i) {
                offered[p.getId()] = results[1][i].map(function (s) { return s.code; });
            });
            var unknown = codes.filter(function (code) {
                return offered[providerId].indexOf(code) < 0;
            });
            if (!unknown.length) {
                return;
            }
            var owner = Ext.Array.findBy(providers, function (p) {
                return p.getId() !== providerId && codes.every(function (code) {
                    return offered[p.getId()].indexOf(code) >= 0;
                });
            });
            me.showFileWarning('<b>' + t('import.mismatchTitle') + '</b>' +
                t('import.mismatch', Ext.String.htmlEncode(unknown.join(', ')), Ext.String.htmlEncode(combo.getSelection().get('name'))) +
                (owner ? ' ' + t('import.belongsTo', Ext.String.htmlEncode(owner.get('name'))) : ''));
        }).catch(Ext.emptyFn); // no warning then; the import report still names every rejected row
    },

    /** Distinct values of the SERVICE column; [] when the header has none (the server reports that). */
    serviceCodes: function (text) {
        var lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter(function (line) {
                return line.trim();
            }),
            column = lines.length ? lines[0].split(',').map(function (name) {
                return name.trim();
            }).indexOf('SERVICE') : -1,
            codes = [];
        if (column < 0) {
            return codes;
        }
        lines.slice(1).forEach(function (line) {
            var code = (line.split(',')[column] || '').trim().replace(/^"(.*)"$/, '$1');
            if (code && codes.indexOf(code) < 0) {
                codes.push(code);
            }
        });
        return codes.sort();
    },

    /** html: the warning, or null to hide it. The button then says that importing goes ahead regardless. */
    showFileWarning: function (html) {
        var t = Appointments.i18n.t, warning = this.down('#fileCheck');
        if (html) {
            Appointments.Api.showBanner(warning, 'warn', html);
        } else {
            warning.hide();
        }
        this.down('#importButton').setText(t(html ? 'import.importAnyway' : 'import.import'));
    },

    updateImportButton: function () {
        this.down('#importButton').setDisabled(!this.file || !this.down('#provider').getValue());
    },

    /** Title and counters (HTML) of the last import. */
    showSummary: function (title, html) {
        this.down('#summary').setHtml('<div class="app-summary"><h2>' + title + '</h2>' + html + '</div>');
        this.down('#summary').show();
    },

    renderReason: function (reason) {
        return '<span class="app-badge ' + (reason === 'DUPLICATE' ? 'app-badge-muted' : 'app-badge-warn') + '">' +
            Ext.String.htmlEncode(this.REASONS[reason] || reason) + '</span>';
    },

    upload: function () {
        var me = this, Api = Appointments.Api, t = Appointments.i18n.t;
        if (!me.down('#uploadForm').isValid() || !me.file) {
            return;
        }
        var providerId = me.down('#provider').getValue();
        me.down('#rejections').setLoading(t('import.importing'));
        Api.importCsv(providerId, me.file).then(function (report) {
            me.showSummary(t('import.lastImportInto', Ext.String.htmlEncode(report.fileName || ''), report.serviceId),
                '<div class="app-counters">' +
                t('import.counters', report.totalRows, report.imported, report.duplicates, report.rejected) + '</div>' +
                (report.status === 'COMPLETED' ? '' : '<div class="app-banner app-banner-error" style="flex: 1 1 100%">' +
                    '<div><b>' + (me.STATUSES[report.status] || report.status) + '</b>' +
                    Ext.String.htmlEncode(report.failureMessage || '') + '</div></div>'));
            me.down('#rejections').setEmptyText(me.emptyHtml('import.noRejected'));
            me.rejectionStore.loadData(report.rejections);
            me.updateRejections();
            if (report.status === 'COMPLETED') {
                // Done, so a second click does not send the file again. Choosing it again is allowed: the server skips
                // every row as a duplicate and says so (ADR 0005). A file that failed part-way stays chosen, since
                // importing it again is the way to finish it.
                me.down('#file').el.down('.app-drop-input').dom.value = '';
                me.setFile(null);
            }
            me.fireEvent('appointmentsimported', providerId);
        }).catch(function (e) {
            // e.g. 500 "Import aborted": rows imported so far stay; importing the file again is safe.
            me.showSummary(t('import.lastImportFailed'), '<div class="app-banner app-banner-error" style="flex: 1 1 100%">' +
                '<div><b>' + t('import.failed') + '</b>' + Ext.String.htmlEncode(e.message) + '</div></div>');
            me.down('#rejections').setEmptyText(''); // the summary above says what went wrong
            me.rejectionStore.removeAll();
            me.updateRejections();
            me.fireEvent('appointmentsimported', providerId);
        }).finally(function () {
            me.down('#rejections').setLoading(false);
        });
    }
});
