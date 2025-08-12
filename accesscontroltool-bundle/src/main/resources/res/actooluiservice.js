/*-
 * #%L
 * Access Control Tool Bundle
 * %%
 * Copyright (C) 2015 - 2025 Cognizant Netcentric
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */
const EVENT_NAME_INSTALLATION_DONE = 'acinstallationdone';
const EVENT_NAME_INSTALLATION_DIALOG_CLOSE = 'acinstallationdialogclose';
const EVENT_NAME_INSTALLATION_MESSAGE = 'acinstallationmessage';

function applyAcToolConfig(isWebConsole, form) {
    openDialog(isWebConsole);
    let formData=form.serialize();
    let xhr = $.post(form.attr('action'), formData);
    xhr.done(function(text) {
        populateViaEventSource(text);
    })
    xhr.fail(function(xhr){
        let message = xhr.status===403?'Permission Denied':'Config could not be applied - check log for errors'
        printMessage(message);
        document.dispatchEvent(new Event(EVENT_NAME_INSTALLATION_DONE));
    });
}

function printMessage(text) {
    document.dispatchEvent( new CustomEvent(EVENT_NAME_INSTALLATION_MESSAGE, { detail:  text }));
}

function populateViaEventSource(url) {
    const evtSource = new EventSource(url, {
        withCredentials: true,
    });
    evtSource.onmessage = (event) => {
        if (event.data === 'END') {
            document.dispatchEvent(new Event(EVENT_NAME_INSTALLATION_DONE));
            evtSource.close();
            return;
        } else {
            printMessage(event.data);
        }
    };
    evtSource.onerror = () => {
        // no reasonable status exposed here, just assume this was a 404 -> regular status code when installation finished
        document.dispatchEvent(new Event(EVENT_NAME_INSTALLATION_DONE));
    }
    document.addEventListener(EVENT_NAME_INSTALLATION_DIALOG_CLOSE, function() {
        evtSource.close();
    });
}

function openDialog(isWebConsole) {
    if (isWebConsole) {
        openDialogJQueryUI();
    } else {
        openDialogCoralUI();
    }
}

function openDialogCoralUI() {
    let dialog = document.getElementById('actool-installation-dialog');
    if (dialog === null) {
        dialog = new Coral.Dialog().set({
            id: 'actool-installation-dialog',
            header: {
              innerHTML: 'Access Control Tool Installation Progress'
            },
            footer: {
              innerHTML: '<coral-wait id="waitWidget"></coral-wait><button is="coral-button" variant="primary" coral-close>Close</button>'
            },
            content: {
                innerHTML: '<div class="coral-Form--vertical" style="height:100%"><section class="coral-Form-fieldset" style="height:100%"><label id="label-textarea-1" class="coral-Form-fieldlabel">Log</label><textarea class="coral-Form-field" style="height:90%" id="log" is="coral-textarea" labelledby="label-textarea-1" readonly value="Installation started...\n"></textarea></section></div>'
            },
            fullscreen: true
        });
        dialog.on('coral-overlay:beforeclose', function() {
           let event = new Event(EVENT_NAME_INSTALLATION_DIALOG_CLOSE);
           document.dispatchEvent(event);
        });
        document.addEventListener(EVENT_NAME_INSTALLATION_MESSAGE, function(event) {
            document.getElementById('log').value += event.detail + "\n";
        });
        document.addEventListener(EVENT_NAME_INSTALLATION_DONE, function() {
            document.getElementById('waitWidget').hide();
        });
        document.body.appendChild(dialog);
    } else {
        document.getElementById('log').value = "Installation started...\n";
        document.getElementById('waitWidget').show();
    }
    dialog.show();
}

function openDialogJQueryUI() {
    let dialog = document.getElementById('actool-installation-dialog');
    if (dialog === null) {
        dialog = document.createElement('div');
        dialog.setAttribute('id', 'actool-installation-dialog');
        dialog.setAttribute('title', 'Access Control Tool Installation Progress');
        dialog.innerHTML = '<textarea id="log" readonly style="height: 90%; width: 100%;">Installation started...\n</textarea><br/><div id="progressbar"></div>';
        document.body.appendChild(dialog);
        $("#actool-installation-dialog").dialog({
            autoOpen: false,
            modal: true,
            width: $(window).width()*0.9,
            height: $(window).height()*0.9,
            buttons: {
               Close: function() {
                   let event = new Event(EVENT_NAME_INSTALLATION_DIALOG_CLOSE);
                   document.dispatchEvent(event);
                   $( this ).dialog( "close" );
               }
            }
        });
        document.addEventListener(EVENT_NAME_INSTALLATION_MESSAGE, function(event) {
            document.getElementById('log').value += event.detail + "\n";
        });
        $("#progressbar").progressbar({
              value: false
        });
        document.addEventListener(EVENT_NAME_INSTALLATION_DONE, function() {
            $("#progressbar.ui-progressbar").progressbar("destroy");
        });
    } else {
        document.getElementById('log').value = "Installation started...\n";
        $("#progressbar").progressbar({
            value: false
        });
    }
    $(dialog).dialog("open");
}
