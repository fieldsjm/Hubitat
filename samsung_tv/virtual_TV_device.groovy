/*
 * Virtual Television Device (WebCore Data)
 *
 *  https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/virtual-presence-switch.src/virtual-presence-switch.groovy
 *
 *  Copyright 2018 Daniel Ogorchock
 *
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * 
 */

metadata {
    definition (name: "Virtual Television Data", namespace: "ogiewon", author: "Daniel Ogorchock", importUrl: "https://raw.githubusercontent.com/ogiewon/Hubitat/master/Drivers/virtual-presence-switch.src/virtual-presence-switch.groovy") {
        capability "Actuator"
        capability "Switch"
		
        attribute "mute","string"
        attribute "inputSource","string"
        attribute "volume","integer"
        attribute "sourceDescripton","string"
        attribute "eventTime","string"
        attribute "tvTile","string"
        
        command "on"
        command "off"
        command "setMute"
        command "setInputSource"
        command "setVolume"
        command "setSourceDescripton"
        command "setEventTime"
    }   
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
    off()
}

def on() {
    state.switch = "on"
    sendEvent(name: "switch", value: state.switch)
}

def off() {
    state.switch = "off"
    sendEvent(name: "switch", value: state.switch)
    def tvTile = tv_Tile(state.switch)
}

def setMute(param1) {
    sendEvent(name: "mute", value: param1)
}

def setInputSource(param2) {
    sendEvent(name: "inputSource", value: param2)
}

def setVolume(param3) {
    sendEvent(name: "volume", value: param3)
}

def setSourceDescripton(param4) {
    sendEvent(name: "sourceDescription", value: param4)
}

def setEventTime(param5) {
    sendEvent(name: "eventTime", value: param5)
}

def tv_Tile () {
    def img = ""
    if (state.switch == "on") {
        img = "tvON.png"
        msg = "Power: ${state.switch}<br>Input: ${inputSouce}<br>Source: ${sourceDescription}<br>Volume: ${volume}<br>Mute: ${mute}<br>Last Update: ${eventTime}"
    } else {
        img = "tvOFF.png"
        msg = "Power: ${state.switch}"
    }
    img = "https://raw.githubusercontent.com/fieldsjm/Hubitat/master/samsung_tv/support/${img}"
    html = "<center><img width=70px height=70px vspace=5px src=${img}><br><font style='font-size:13px'>"${msg}"</font></center>"
    sendEvent(name: "tvTile", value: html, displayed: true)
}
        
