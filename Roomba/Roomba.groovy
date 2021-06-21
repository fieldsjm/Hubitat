/*iRobot Roomba v3.1
*. 800/900 series - Virtual Switch
*
*  Copyright 2016 Steve-Gregory
*  Modified by Adrian Caramaliu to add support for v2 local API
*  Modified by Jonathan Fields local API only for HE
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*  Changes:
*  3.0 - removed cloud component
*  3.1 - added dashboard tile support
*/

metadata {
    definition (name: "Roomba", namespace: "fieldsjm", author: "Steve Gregory & Adrian Caramaliu") {
        capability "Battery"
        capability "Switch"
        capability "Refresh"
        capability "Polling"
        capability "Consumable"
        capability "Configuration"

        command "start"
        command "stop"
        command "dock"
        command "resume"
        command "pause"
        command "cancel"

        attribute "robotName", "string"
        attribute "consumableStatus", "ENUM",["Missing","Full","Ready"]
        attribute "runtimeMins", "string"
        attribute "preferences_set", "string"
        attribute "status", "string"
        attribute "APIstatus", "string"
        attribute "RoombaTile", "string"
        attribute "LastStart", "string"
        
    }
}

//Preferences
preferences {
        input "roomba_host", "string", title:"IP of Roomba local REST Gateway", displayDuringSetup: true
        input "roomba_port", "number", range: "1..65535", defaultValue: 3000, title:"Port of Roomba local REST Gateway", displayDuringSetup: true
        input "pollInterval", "number", title: "Polling Interval", description: "Change polling frequency (in seconds [60-600])", defaultValue:240, range: "60..600", required: true, displayDuringSetup: true
        input name: "debugOutput", type: "bool", title: "Enable debug logging", defaultValue: true    
}

// Settings updated
def updated() {
    unschedule()
    initialize()
    if (debugOutput) runIn(1800,logsOff) //disable debug logs after 30 min
	log.trace "Msg: updated ran"
}

//Installed
def installed() {
	initialize()
}

// Configuration
def configure() {
    log.debug "Configuring.."
    poll()
}

def initialize() {
    poll()
    sendEvent(name: 'switch', value: 'off')
}

//Refresh
def refresh() {
    log.debug "Executing 'refresh'"
    return poll()
}
//Polling
def poll() {
    //log.debug "Polling for status ----"
    local_poll()
}

// Switch methods
def on() {
    // Always start roomba
    def status = device.latestValue("status")
    sendEvent(name: 'switch', value: 'on') 
    if(status == "paused") {
	    return resume()
    } else {
	    return start()
    }
}
def off() {
    // Always return to dock..
    sendEvent(name: 'switch', value: 'off') 
    return dock()
}

//Commands
def start() {
    def date = new Date()
    //state.startTime = date
    def sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm aa")
    sendEvent(name: "status", value: "starting")
    sendEvent(name: 'switch', value: 'on') 
    sendEvent(name: "LastStart", value: sdf.format(date), isStateChange: true)
    runIn(15, poll)
	local_start()
}
def stop() {
    sendEvent(name: "status", value: "stopping")
    sendEvent(name: 'switch', value: 'off') 
    runIn(15, poll)
    local_stop()
}
def pause() {
    sendEvent(name: "status", value: "pausing")
    runIn(15, poll)
    local_pause()
}
def cancel() {
	return off()
}

def dock() {
    sendEvent(name: "status", value: "docking")
    runIn(15, poll)
    return local_dock()
}
def resume() {
    sendEvent(name: "status", value: "resuming")
    runIn(15, poll)
    return local_resume()
}

// API methods
def parse(description) {
	log.trace "GOT HERE"
    def msg = parseLanMessage(description)
    log.trace "GOT MSG $msg"
    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)
}

/* local REST gw support */

def lanEventHandler(evt) {
	log.trace "GOT HERE"
    def description = evt.description
    def hub = evt?.hubId
	def parsedEvent = parseLanMessage(description)
	log.trace "RECEIVED LAN EVENT: $parsedEvent"
}

private local_get(path, cbk) {
	//def host = "$roomba_host:$roomba_port"
	//new hubitat.device.HubAction("""GET $path HTTP/1.1\r\nHOST: $host\r\n\r\n""", hubitat.device.Protocol.LAN, null, [callback: cbk])
    
    try{
        httpGet([uri: "http://$roomba_host:$roomba_port$path"]){ resp -> 
            if (debugOutput) log.debug "Response ${resp.data}"
            "$cbk"(resp.data)
            sendEvent(name: "APIstatus", value: "Online", displayed: false)
        }
    } catch (e) {
        if (debugOutput) log.debug "Device Unresponsive - Check Rest980 | Robot"
        sendEvent(name: "APIstatus", value: "Device Unresponsive - Check Rest980 | Robot", displayed: false)
    }
}

def new_status(readyCode, current_phase, current_charge) {
    
    if(readyCode != 0) {
		if(readyCode == 16) {
            return "Bin is full. Empty bin to continue."
        } else if(readyCode == 7) {
            return "Place robot on flat surface to continue."
        } else if (readyCode == 3) {
            return "Stuck. Move robot to continue."
        } else if (readyCode == 15) {
            if (current_phase == "charge") {
                return "Low Battery. Docked/Charging."
            } else {
                return "Low Battery. Place robot on charger."
            }
        } else if (readyCode == 6) {
            return "Extractors jammed. Clear to continue."
        } else {
            return "Not Ready. See iRobot app for details."
        }
    } else if (current_phase == "charge") {
		if (current_charge == 100) {
			return "Docked/Fully Charged"
		} else {
			return "Docked/Charging"
        }
	} else if(current_phase == "hmUsrDock") {
		return "Docking"
	} else if(current_phase == "pause" || current_phase == "stop") {
		return "Stopped"
	} else if(current_phase == "run") {
        	return "Cleaning"
	} else {
		return "Unknown Error. See iRobot app for details."
	}
}

void local_dummy_cbk(data) {
}

void local_poll_cbk(data) {
    def current_charge = data.batPct
    def robotName = data.name
	    state.robotName = robotName    
    def mission = data.cleanMissionStatus
        def current_phase = mission.phase
        //def num_mins_running = mission.mssnM
        def startTime = mission.mssnStrtTm
        def readyCode = mission.notReady
    def bin = data.bin
     	def bin_full = bin.full
        def bin_present = bin.present
    
    if(current_phase == "run") {
        def current = new Date().getTime()
        def duration = Math.round(((current/1000) - startTime)/60)
        state.duration = duration
    } else {
        state.duration = device.latestValue(state.duration)   
    }
    
    def roomba_value = new_status(readyCode, current_phase, current_charge)
    def roombaTile = roomba_tile(roomba_value, current_charge)
    
    if (debugOutput) log.debug("Robot updates -- ${roomba_value}")
    
    //Set the state object
    if(roomba_value == "Cleaning") {
        state.switch = "on"
    } else {
        state.switch = "off"
    }   

    /* Consumable state-changes */
    if(bin_present == false) {
        state.consumable = "Missing"
    } else if(bin_full == true){
        state.consumable = "Full"
    } else {
        state.consumable = "Ready"
    }
    
    /*send events, display final event*/
    sendEvent(name: "robotName", value: robotName, displayed: false)
    sendEvent(name: "runtimeMins", value: state.duration, displayed: false)
    sendEvent(name: "battery", value: current_charge, displayed: false)
    sendEvent(name: "consumableStatus", value: state.consumable)
    sendEvent(name: "status", value: roomba_value)
    sendEvent(name: "switch", value: state.switch)
}

private local_poll() {
	local_get('/api/local/config/preferences', 'local_poll_cbk')
    runIn(pollInterval, poll)
}

private local_start() {
	local_get('/api/local/action/start', 'local_dummy_cbk')
}

private local_stop() {
	local_get('/api/local/action/stop', 'local_dummy_cbk')
}

private local_pause() {
	local_get('/api/local/action/pause', 'local_dummy_cbk')
}

private local_resume() {
	local_get('/api/local/action/resume', 'local_dummy_cbk')
}

private local_dock() {
	local_get('/api/local/action/dock', 'local_dummy_cbk')
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("debugOutput",[value:"false",type:"bool"])
}

def roomba_tile(roomba_value, current_charge) {
    def img = ""
    switch(roomba_value) {
        case "Cleaning":
            img = "roomba-clean.png"
            msg=roomba_value
            break
        case "Stopped":
            img = "roomba-stop.png"
            msg=roomba_value
            break        
        case  ~/.*Docked.*/:
            img = "roomba-charge.png"
	    msg=roomba_value
            break        
        case "Docking":
            img = "roombadock.png"
            msg=roomba_value
            break
        case  ~/.*battery.*/:
            img = "roomba-dead.png"
            msg=roomba_value
            break
        default:
            img = "roomba-error.png"
            msg=roomba_value
            break
    }
    img = "https://raw.githubusercontent.com/fieldsjm/Hubitat/master/Roomba/support/${img}"
    html = "<center><img width=70px height=70px vspace=5px src=${img}><br><font style='font-size:13px'>"
        if(roomba_value.contains("Docking") || roomba_value.contains("Cleaning")) html +="${msg} - ${state.duration} min<br>Battery: ${current_charge}%"
    else html+="${msg}<br>Battery: ${current_charge}%<br>Bin: ${state.consumable}"
    html += "</font></center>"
    sendEvent(name: "RoombaTile", value: html, displayed: true)
    if(logEnable) log.debug "Roomba Status of '${msg}' sent to dashboard"
}

import java.text.SimpleDateFormat
