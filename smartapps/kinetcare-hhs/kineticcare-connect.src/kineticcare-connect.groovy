/**
 *  KineticCare Home Health Station Manager
 *
 *  Author: KineticCare
 *
 *  Copyright 2015 KineticCare
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
definition(
	name: "KineticCare (connect) ",
	namespace: "kinetcare.hhs",
	author: "KineticCare, LLC",
	description: "Allows you to attach to a KineticCare Home Health Station and integrate it with your SmartThings home automation products",
	category: "Health & Wellness",
	iconUrl: "http://kineticcare.blob.core.windows.net/icons/Logo64x64.png",
	iconX2Url: "http://kineticcare.blob.core.windows.net/icons/Logo128x128.png",
    singleInstance: true
)

preferences {
	page(name:"mainPage", title:"Hue Device Setup", content:"mainPage", refreshTimeout:5)
	page(name:"hhsDiscovery", title:"Hue Bridge Discovery", content:"hhsDiscovery", refreshTimeout:5)
	page(name:"hhsAdded", title:"Configure", content:"hhsAdded")
	page(name:"medicationsPage", title:"Configure Medications", content:"medicationsPage")
	page(name:"alertsPage", title:"Configure Alerts", content:"alertsPage")
    page(name:"clearAllHHSPage", title:"Clear All HHS", content:"clearAllHHSPage")
	page(name:"appointmentsPage", title:"Configure Appoitnment", content:"appointmentsPage")
	page(name:"page1", title:"Meds", content:"page1")
	page(name:"page2", title:"Life Warning", content:"page2")
	page(name:"page3", title:"Life Alert", content:"page3")
}

def mainPage() {
	log.debug "App Starting"
    def bridges = bridgesDiscovered()
    def bridgeCount = getHueBridges().size()
 //   if (bridgeCount > 0) {
        return hhsAdded()
   // } else {
     //   return hhsDiscovery()
   // }
}

private discoverBridges() {
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:basic:1", physicalgraph.device.Protocol.LAN))
}

def hhsDiscovery(params=[:]){
	log.debug "App Discovery 2"
    alertTriggerButton = []
    def devices = getAllChildDevices();
    devices.each {
    	log.debug it.id
 //   	deleteChildDevice(it)
    }
    
	def bridges = bridgesDiscovered()
	int bridgeRefreshCount = !state.bridgeRefreshCount ? 0 : state.bridgeRefreshCount as int
	state.bridgeRefreshCount = bridgeRefreshCount + 1
	def refreshInterval = 3

	def options = bridges ?: []
	def numVerified = options.size() ?: 0

	def potentialFound = getHueBridges().size()
    
    subscribe(location, null, locationHandler, [filterEvents:false])

	discoverBridges()
	verifyHueBridges()

	return dynamicPage(name:"hhsDiscovery", title:"Discovery Started!", nextPage:"hhsAdded", refreshInterval:refreshInterval, uninstall: true) {
		section("Please wait while we discover your Home Health Station. Discovery can take five minutes or more! Select your device below once discovered.") {        
        	paragraph "Potential ${potentialFound} Verified: ${numVerified}"
			input "selectedHue", "enum", required:false, title:"Select HHS (${numVerified} found)", multiple:false, options:options
		}
        section("Options") {
        	href(name:"href", title:"Clear HHS", required: false, page: "clearAllHHSPage");
        }
	}
}

def hhsAdded(){
	return dynamicPage(name:"hhsAdded", title:"Home Health Station Added", nextPage:"", install:true, uninstall: true) {
		section("Configure Alerts.") {
        	href(name:"href", title:"Medication Reminders",style:"page",  page: "medicationsPage");
        }
        section("Configure Alerts.") {
            href(name:"href", title:"Alerts",style:"page",  page: "alertsPage");
        }
        
        section("Configure Alerts.") {
            href(name:"href", title:"Add Another Home Helth Station",style:"page",  page: "hhsDiscovery");
        }
	    section("Configure Alerts.") {
        	href(name:"href", title:"Appointment Reminders", style:"page", page: "appointmentsPage");
       		href(name:"href", title:"Meds", style:"page", page: "page1");
       		href(name:"href", title:"Life Warning", style:"page", page: "page2");
       		href(name:"href", title:"Life Alert", style:"page", page: "page3");
        }
    }
}

def clearAllHHSPage() {
	log.debug "Clear All"

    state.bridges = []
    state.brdigeRefreshCount = 0

  	def allDevices = getAllChildDevices()
    allDevices.each{
    	log.debug "Remove device ${it.deviceNetworkId}"
        try{
       		deleteChildDevice(it.deviceNetworkId)
        }
        catch(e) {
        	log.debug(e)
        }
    }
    
   	return dynamicPage(name:"hhsAdded", title:"Home Health Station Added", nextPage:"", install:false, uninstall: false) {
		section("State Removed.") {
			paragraph("State has been Removed, please re-find your hubs.")
		}
     }
}

def medicationsPage() {
	return dynamicPage(name:"medicationsPage", title:"Medications", nextPage:"", install:false, uninstall: false) {
    	section("Trigger") {
			input "medsReminderTriggerButton", "capability.button", title: "What button press should trigger an alert?", required: false, multiple:true
	   	}
    	section("Reminder") {
        	paragraph("How should you be reminded?")
            
            
    			input "hues", "capability.colorControl", title: "Which Bulbs?", required:false, multiple:true
    			input "medsReminderHues", "capability.colorControl", title: "Which Bulbs?", required:false, multiple:true
				input "medsReminderColor", "enum", title: "Hue Color?", required: false, multiple:false, options: 
						["Red","Green","Blue","Yellow","Orange","Purple","Pink"]
				input "medsReminderSpeakers", "capability.musicPlayer", title: "Which Speakers?", required:false, multiple:true
				input "medsReminderVolume", "number", title: "Set the volume volume", description: "0-100%", required: false, defaultValue: 100
                input "medsReminderUseDefault", "bool", title: "Use Default Message", required:false, multiple:false, defaultValue:true
				input "medsReminderMessage", "phrase", title: "Say what message", required:false, multiple:false
       			input "medsReminderCount", "number", title: "How may times to remind?", required: false, defaultValue: 2
       			input "medsReminderInterval", "number", title: "Interval (sec.) between reminder", required:false, defaultValue: 5
       }
       section("Clearing") {
			paragraph("How do you want to notify that you medication has been taken?")
			input "medicationContactClearReminder", "capability.contactSensor", title: "Open/Close", required:false, multiple:true
    		input "medicationButtonClearReminder", "capability.button", title: "Button", required:false, multiple:false
       }
       section("Notification") {
        	paragraph("Who/how should be notified if medications are not taken?")
        	input "medsTextNumber", "number", title: "Phone Number to Notify?", required: false, multiple:false
       		input "medsTextMessage", "phrase", title: "Send what message", required: false, multiple:false
	   }
    }
}

def alertsPage(){
  	return dynamicPage(name:"alertsPage", title:"Alerts", nextPage:"", install:false, uninstall: false) {
    	section("Trigger") {
	   		input "alertWarningTriggerButton", "capability.button", title: "What button press should trigger an alert with alert only?", required: false, multiple:true
	   		input "alertTriggerButton", "capability.button", title: "What button press should trigger an alert?", required:false, multiple:true
	   		input "alertTriggerContactSensor", "capability.contactSensor", title: "What contact open should trigger alert?", required: false, multiple:true
	    }
		section("Warning") {
        	paragraph("How should you be warned prior to notify first responders?")
       			input "alertWarningHues", "capability.colorControl", title: "Which Bulbs?", required:false, multiple:true
				input "alertWarningColor", "enum", title: "Hue Color?", required: false, multiple:false, options:  ["Red","Green","Blue","Yellow","Orange","Purple","Pink"]
				input "alertWarningSpeakers", "capability.switch", title: "Which Speakers?", required:false, multiple:true
				input "alertWarningVolume", "number", title: "Set the volume volume", description: "0-100%", required: false, defaultValue: 100
            	input "alertWarningUseDefault", "bool", title: "Use Default Message", required:false, multiple:false, defaultValue:true
				input "alertWarningMessage", "phrase", title: "Say what message", required:false, multiple:false
       			input "alertWarningCount", "number", title: "How may times to warn?", required: false, defaultValue: 4
       			input "alertWarningInterval", "number", title: "Interval (sec.) between reminder", required: false, defaultValue: 5
        }
    	section("Clearing") {
   			input "alertWarningClear", "capability.button", title: "Clear Warning", required:false, multiple:true
        }
        section("Notification") {
    		input "alertNotifyAlarm", "capability.switch", title: "Which Alarm?", description: "0-100%", required: false
			input "speaker", "capability.switch", title: "Which Alarm?", description: "0-100%", required: false
			input "alertNotifAlarm", "capability.switch", title: "Which Alarm?", description: "0-100%", required: false
	    	input "alertNotifyOnLights", "capability.switch", title: "Turn On Which Lights?", required: false, multiple:true
	    	input "alertNotifyFlashLights", "capability.switch", title: "Flash Which Lights?", required: false, multiple:true
            input "alertNotifyUnlockDoors", "capability.lock", title: "Unlock Which Door?", required: false, multiple:true            
	    	input "alertNotifTextNumber", "number", title: "Phone Number to Notify?", required: false, multiple:true
            input "alertNotifTextMessage", "string", title: "Message To Send", required: false, multiple:false
	    	input "alertNotifyClear", "capability.button", title: "How to Clear?", required: false, multiple:true
	    }
    }
}


def page1() {
  	startMedsReminderAlert()
      
  	return dynamicPage(name:"page1", title:"Meds Warning", nextPage:"", install:false, uninstall: false) { }
}

def page2() {
	alert911HandlerWarning1()

  	return dynamicPage(name:"page2", title:"Safe Warning", nextPage:"", install:false, uninstall: false) { }
}


def page3() {
	alert911HandlerWarning2()

  	return dynamicPage(name:"page2", title:"Bad Warning", nextPage:"", install:false, uninstall: false) { }
}

def appointmentsPage() {
  	return dynamicPage(name:"appointmentsPage", title:"Appointments", nextPage:"", install:false, uninstall: false) {
    	section("Reminder") {
        	paragraph("How should you be reminded.")
 	    }
    	section("Clearing") {
 			paragraph("What should be used to clear the reminder.")
      	}
 		section("Notification") {
        	paragraph("Who should bed notified.")
        }
    }
}

private sendDeveloperReq() {
	def token = app.id
    def host = getBridgeIP()
	sendHubCommand(new physicalgraph.device.HubAction([
		method: "POST",
		path: "/api",
		headers: [
			HOST: host
		],
		body: [devicetype: "$token-0", username: "$token-0"]], "${selectedHue}"))
}

private verifyHueBridge(String deviceNetworkId, String host) {
	log.debug "Sending meta data request to $host"

		sendHubCommand(new physicalgraph.device.HubAction([
		method: "GET",
		path: "/xml/props.xml",
		headers: [
			HOST: host
		]], deviceNetworkId))
}

private verifyHueBridges() {
	
	def devices = getHueBridges().findAll { it?.value?.verified != true }
    def deviceCount = devices.size()
    
	devices.each {
        def ip = convertHexToIP(it.value.networkAddress)
        def port = convertHexToInt(it.value.deviceAddress)
		verifyHueBridge("${it.value.mac}", (ip + ":" + port))
	}
}

Map bridgesDiscovered() {
	def vbridges = getVerifiedHueBridges()
	def map = [:]
	vbridges.each {
		def value = "${it.value.name}"
		def key = "${it.value.mac}"
		map["${key}"] = value
	}
	map
}

def getHueBridges() {
	state.bridges = state.bridges ?: [:]
}

def getVerifiedHueBridges() {
	getHueBridges().findAll{ it?.value?.verified == true }
}

def installed() {
	log.trace "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.trace "Updated with settings: ${settings}"
	unsubscribe() 
    unschedule()    
	initialize()
}

def initialize() {
	log.debug "Initializing"  
    unsubscribe(bridge)
    subscribeToEvents()
    state.inBulbDiscovery = false
    state.bridgeRefreshCount = 0
    state.bulbRefreshCount = 0
	if (selectedHue) {
   		addBridge()
        addBulbs()
        doDeviceSync()
        runEvery5Minutes("doDeviceSync")
	}
}

def subscribeToEvents() {
	log.debug("SUBSCRIBING")

	unsubscribe()
    def hhsList = getChildDevices()
    hhsList.each {    
    	log.debug "Added alert handler ${it.deviceNetworkId}"
    	subscribe(it, "button", hhsButtonHandler)
    }
    
    subscribe(medsReminderTriggerButton, "button", medsReminderButtonHandler)
    subscribe(alertWarningTriggerButton, "button", medsReminderButtonHandler)
    subscribe(alertTriggerButton, "button", medsReminderButtonHandler)
}

def medsReminderButtonHandler(evt) {
	log.debug "Received Name ${evt.name}"
    log.debug "Received Data ${evt.data}"
	log.debug "Received Event Type ${evt.value}"
    
   	def body = new groovy.json.JsonSlurper().parseText(evt.data)
      
    log.debug "Received Data ${body.buttonNumber}"
    
    switch(body.buttonNumber) {
 		case 1:
        	startMedsReminderAlert()
        	break;
        case 2:
        	alert911HandlerWarning1()
        	break;
        case 3:
        	alert911HandlerWarning2()
        	break;
        case 4:
        	//alertNotifyOnLights.on()
         	alertNotifyOnLights.off()
            medsReminderHues.off()
            alertNotifyAlarm.off()
          //  alertNotifyUnlockDoors.lock()
            break;
 	}
}

def hhsButtonHandler(evt) {
	log.debug "Received Name ${evt.name}"
    log.debug "Received Data ${evt.data}"
	log.debug "Received Event Type ${evt.value}"
    
    switch(evt.data) {
    	case "911alert" :
        	alert911HandlerWarning2()
        	break;

    	case "meds" :
        	startMedsReminderAlert()
        	break;
            
    	case "needHelp" :
        
        	break;
            
        case "vitals" : 
        	
            break;
            
        case "cleared" : 
        	clearCondition()
        	break;
    }
}	

meds

def startVitalsReminder() {
	log.debug "Received Name ${evt.name}"
    log.debug "Received Data ${evt.data}"
	log.debug "Received Event Type ${evt.value}"
}

def startMedsReminderAlert() {
	log.debug "Starting Meds Reminder Process "
    
    state.flashMedsReminderCount = 0
	setHuesColor(medsReminderHues, medsReminderColor)
	state.medsAlertReminder = 'active'
    medsReminderHues.on()
    runIn(1, medsReminderFlash)
    
    if(medsReminderSpeakers.size() == 0){
    	log.debug "No Speakers, can not play reminder."
    }
    else if(medsReminderUseDefault) {
    	log.debug "Playing Default Sound"
    	def sound = [uri: "http://kineticcare.blob.core.windows.net/audio/Pills.mp3", duration: "8"]
        medsReminderSpeakers.playTrackAndResume(sound.uri, sound.duration, 100)
    }
    else {	
    	def sound = textToSpeech(medsReminderMessage)
        log.debug "Playing The Message Message ${sound.duration}"
    	medsReminderSpeakers.playTrackAndResume(sound.uri, sound.duration, 100)
    }
}

def medsReminderAlert() {
	if(state.medsAlertReminder == 'active') {


	}
}

def triggerMedsNotification() {
    if(state.medsAlertReminder == 'active') {        
        medsTextNumber.each {
            sendSms(it, medsTextMessage)
        }        
        state.medsAlertReminder = 'cleared'
    }
}

def medsReminderFlash() {
	def count = 10
    log.debug("flash")

	while(count > 0){
        if(state.medsAlertReminder == 'active') {	
            state.flashMedsReminderOn = !state.flashMedsReminderOn
            if(state.flashMedsReminderOn) {
                log.debug("flash - on")
                medsReminderHues.off()
            }
            else {
                log.debug("flash - of")
                medsReminderHues.on()
            }

            state.flashMedsReminderCount = state.flashMedsReminderCount + 1

            pause(1000)                       
            count = count - 1
        }
        else {
        	count = 0
            medsReminderHues.off()
        }
    }
    
    medsReminderHues.off()
    
	runIn(1, triggerMedsNotification)
}

def medsTaken() {
	state.medsAlertReminder = 'cleared'
}

def medsNotTakenHandler() {

}

/* Note 911 doesn't really mean phone 911, but used as a emergency scenario */
def alert911HandlerWarning1() {
	setHuesColor(alertWarningHues, alertWarningColor) 
    alertNotifyFlashLights.on()
    state.flashAlertNotifyCount = 0
    state.flashAlertNotifyOn = true
    
    state.alert911 = 'active'
    
     if(alertWarningSpeakers.size() == 0){
    	log.debug "No Speakers, can not play reminder."
    }
    else if(alertWarningUseDefault) {
    	log.debug "Playing default alert sound"
    	def sound = [uri: "http://kineticcare.blob.core.windows.net/audio/Alright.mp3", duration: "5"]
        alertWarningSpeakers.playTrackAndResume(sound.uri, sound.duration, 100)
        log.debug "Played alert sound"
    }
    else {	
    	def sound = textToSpeech(alertWarningMessage)
        log.debug "Playing The Message Message ${sound.duration}"
    	alertWarningSpeakers.playTrackAndResume(sound.uri, sound.duration, 100)
    }
    
    flashAlertNotifyLight1()    
}

def alert911HandlerWarning2() {
	setHuesColor(alertWarningHues, alertWarningColor) 
    alertNotifyFlashLights.on()
    state.flashAlertNotifyCount = 0
    state.flashAlertNotifyOn = true
    
    state.alert911 = 'active'
    
     if(alertWarningSpeakers.size() == 0){
    	log.debug "No Speakers, can not play reminder."
    }
    else if(alertWarningUseDefault) {
    	log.debug "Playing default alert sound"
    	def sound = [uri: "http://kineticcare.blob.core.windows.net/audio/Alright.mp3", duration: "5"]
        alertWarningSpeakers.playTrackAndResume(sound.uri, sound.duration, 100)
        log.debug "Played alert sound"
    }
    else {	
    	def sound = textToSpeech(alertWarningMessage)
        log.debug "Playing The Message Message ${sound.duration}"
    	alertWarningSpeakers.playTrackAndResume(sound.uri, sound.duration, 100)
    }
    
    flashAlertNotifyLight2()    
}

def clearCondition() {
	log.debug "Clearing "
    state.medsAlertReminder = 'cleared'
    state.alert911 = 'cleared'
    medsReminderHues.off()
    alertNotifyFlashLights.off()
	alertNotifyAlarm.off()
}

def alert911Notification() {

}

def shutOffAlarm() {
	log.debug("Shutting Off Alarm")
	alertNotifAlarm.off()
//    alertNotifyOnLights.off()
//    alertNotifyUnlockDoors.lock()
}

def triggerAlarm() {
 	if(state.alert911 == 'active') {
    	log.debug("Triggering Alarm State")
        alertWarningHues.on()
       	alertNotifyAlarm.both()
        alertNotifyUnlockDoors.unlock()
        alertNotifyOnLights.on()
    
        alertNotifTextNumber.each {
            sendSms(it, alertNotifTextMessage)
        }

		pause(1500)
        shutOffAlarm()
    }
}

def flashAlertNotifyLight1() {
	log.debug('notify light')
    
    def count = 6
    while(count > 0){	
        if(state.alert911 == 'active') {
            state.flashAlertNotifyOn = !state.flashAlertNotifyOn

            if(state.flashAlertNotifyOn){
                log.debug('notify light - off')
                alertWarningHues.off()            
            }
            else {
                log.debug('notify light - on')
                alertWarningHues.on()
                state.flashAlertNotifyCount = state.flashAlertNotifyCount + 1
            }
            count = count - 1
            pause(1000)
        }
        else {
        	count = 0
        }        
    }
    
   // if(state.alert911 == 'active') 
    //	runIn(1, triggerAlarm)
}

def flashAlertNotifyLight2() {
	log.debug('notify light')
    
    def count = 4
    while(count > 0){	
        if(state.alert911 == 'active') {
            state.flashAlertNotifyOn = !state.flashAlertNotifyOn

            if(state.flashAlertNotifyOn){
                log.debug('notify light - off')
                alertWarningHues.off()            
            }
            else {
                log.debug('notify light - on')
                alertWarningHues.on()
                state.flashAlertNotifyCount = state.flashAlertNotifyCount + 1
            }
            count = count - 1
            pause(1000)
        }
        else {
        	count = 0
        }        
    }
    
    if(state.alert911 == 'active') 
    	runIn(1, triggerAlarm)
}
	
def setHuesColor(hues, color) {
	def hueColor = 0
	def saturation = 100

	switch(color) {
		case "White":
			hueColor = 52
			saturation = 19
			break;
		case "Daylight":
			hueColor = 53
			saturation = 91
			break;
		case "Soft White":
			hueColor = 23
			saturation = 56
			break;
		case "Warm White":
			hueColor = 20
			saturation = 80 //83
			break;
		case "Blue":
			hueColor = 70
			break;
		case "Green":
			hueColor = 39
			break;
		case "Yellow":
			hueColor = 25
			break;
		case "Orange":
			hueColor = 10
			break;
		case "Purple":
			hueColor = 75
			break;
		case "Pink":
			hueColor = 83
			break;
		case "Red":
			hueColor = 100
			break;
	}
    
    def newValue = [hue: hueColor, saturation: saturation, level: 100]
    hues*.setColor(newValue)
}

def manualRefresh() {
    unschedule()
	unsubscribe()
    doDeviceSync()
    runEvery5Minutes("doDeviceSync")
}

def uninstalled(){
	state.bridges = [:]
    state.username = null
}

def addBridge() {
	def vbridges = getVerifiedHueBridges()
	def vbridge = vbridges.find {"${it.value.mac}" == selectedHue}

	if(vbridge) {
		def d = getChildDevice(selectedHue)
		if(!d) {
     		// compatibility with old devices
            def newbridge = true 
            childDevices.each {
            	if (it.getDeviceDataByName("mac")) {
                    def newDNI = "${it.getDeviceDataByName("mac")}"
                    if (newDNI != it.deviceNetworkId) {
                    	def oldDNI = it.deviceNetworkId
                        log.debug "updating dni for device ${it} with $newDNI - previous DNI = ${it.deviceNetworkId}"
                        it.setDeviceNetworkId("${newDNI}")
                        if (oldDNI == selectedHue)
                        	app.updateSetting("selectedHue", newDNI)
                        newbridge = false 
                    }
                }    
            }  
        	if (newbridge) {
				d = addChildDevice("smartthings", "Hue Bridge", selectedHue, vbridge.value.hub)
 				log.debug "created ${d.displayName} with id ${d.deviceNetworkId}"
                def childDevice = getChildDevice(d.deviceNetworkId)
                childDevice.sendEvent(name: "serialNumber", value: vbridge.value.serialNumber)
 				if (vbridge.value.ip && vbridge.value.port) {
                	if (vbridge.value.ip.contains(".")) {
                    	childDevice.sendEvent(name: "networkAddress", value: vbridge.value.ip + ":" +  vbridge.value.port)
                        childDevice.updateDataValue("networkAddress", vbridge.value.ip + ":" +  vbridge.value.port)
                    } else {
                    	childDevice.sendEvent(name: "networkAddress", value: convertHexToIP(vbridge.value.ip) + ":" +  convertHexToInt(vbridge.value.port))	
                        childDevice.updateDataValue("networkAddress", convertHexToIP(vbridge.value.ip) + ":" +  convertHexToInt(vbridge.value.port))
                    }    
				} else {
					childDevice.sendEvent(name: "networkAddress", value: convertHexToIP(vbridge.value.networkAddress) + ":" +  convertHexToInt(vbridge.value.deviceAddress))
                    childDevice.updateDataValue("networkAddress", convertHexToIP(vbridge.value.networkAddress) + ":" +  convertHexToInt(vbridge.value.deviceAddress))
                }    
			}
		} else {
			log.debug "found ${d.displayName} with id $selectedHue already exists"
		}
	}
}

private subscribeToHHS(){
  
}
def locationHandler(evt) { }
/*
	def description = evt.description
   
	def hub = evt?.hubId
 	def parsedEvent = parseLanMessage(description)
	log.debug parsedEvent;
    return;
    
    parsedEvent << ["hub":hub]
    
    def ip = convertHexToIP(parsedEvent.networkAddress)
    def port = -1
    if(parsedEvent.deviceAddress)
    	port = convertHexToInt(parsedEvent.deviceAddress)
    else
    	port = convertHexToInt(parsedEvent.port)
    
    def host = ip + ":" + port

	log.trace "HTTP Response From Device -> $ip - $port"

	if (parsedEvent?.ssdpTerm?.contains("urn:schemas-upnp-org:device:Basic:1")) {
    	//SSDP DISCOVERY EVENTS
		log.trace "SSDP DISCOVERY EVENTS ${parsedEvent.ssdpUSN.toString()}"
		def bridges = getHueBridges()
		if (!(bridges."${parsedEvent.ssdpUSN.toString()}")) {
        	//bridge does not exist 
			log.trace ">>>>>>>Adding bridge ${parsedEvent.ssdpUSN}"
			bridges << ["${parsedEvent.ssdpUSN.toString()}":parsedEvent]
		} else {
			// update the values
            def countBridges = bridges.size()
			log.debug "Device ($parsedEvent.mac) already exists in bridge ip = $host total brdiges $countBridges"
            def hubId = evt?.hubId;
          	sendHubCommand(new physicalgraph.device.HubAction([
                method: "GET",
                path: "/stsubscribe/$hubId",
                headers: [
                    HOST: host
                ]], deviceNetworkId))   
            
            def dstate = bridges."${parsedEvent.ssdpUSN.toString()}"
			def dni = "${parsedEvent.mac}"
            def d = getChildDevice(dni)
            def networkAddress = null
            if (!d) {
            	log.debug "Did not find matching Device By Mac Address"
            	childDevices.each {
                	log.debug "In Child Devices Loop"
                    if (it.getDeviceDataByName("mac")) {
                    	log.debug "Got Device By Name"
                        def newDNI = "${it.getDeviceDataByName("mac")}"
                        if (newDNI != it.deviceNetworkId) {
                        	log.debug "Network Id Did Not Match"
                            def oldDNI = it.deviceNetworkId
                            log.debug "updating dni for device ${it} with $newDNI - previous DNI = ${it.deviceNetworkId}"
                            it.setDeviceNetworkId("${newDNI}")
                            if (oldDNI == selectedHue)
                                app.updateSetting("selectedHue", newDNI)
                            doDeviceSync()
                        }
                        else
                        	log.debug "Did Not Do Device Sync"
                    }
                    else
                    	log.debug "Did Not Get Device By Name"
                }
                log.debug "Done with Child Device Loop"
			} else {
            	if (d.getDeviceDataByName("networkAddress"))
                	networkAddress = d.getDeviceDataByName("networkAddress")
            	else
                	networkAddress = d.latestState('networkAddress').stringValue
                    
                log.trace "Found child device ${d.displayName} with matching Mac Address: $host - $networkAddress"
                
                d.sendEvent(name:"networkAddress", value: host)
                d.updateDataValue("networkAddress", host)
                d.on()
                
                log.trace "Updated Data Value"
            
                if(host != networkAddress) {
                    log.debug "Device's port or ip changed for device $d..."
                    dstate.ip = ip
                    dstate.port = port
                    dstate.name = "Philips hue ($ip)"
            	} 
            }
		}
	}
	else if (parsedEvent.headers && parsedEvent.body) {
		log.trace "NON SSDP RESPONSE!" 
        log.trace parsedEvent.body
		def headerString = parsedEvent.headers.toString()
		if (headerString?.contains("xml")) {
            log.trace "description.xml response (application/xml)"
			def body = new XmlSlurper().parseText(parsedEvent.body)
			if (body?.device?.modelName?.text().startsWith("HHS01")) {
            	log.trace "++++++FOUND HHS01!!!!!"
                log.trace "Mac Address of response => $parsedEvent.mac"
                
                
				def bridges = getHueBridges()
				def bridge = bridges.find {it?.key?.contains(body?.device?.UDN?.text())}
				if (bridge) {
					bridge.value << [name:body?.device?.friendlyName?.text(), serialNumber:body?.device?.serialNumber?.text(), verified: true]
                    log.trace "NAME OF HHS => $bridge.value"
                    
                 	def d = getChildDevice(dni)
                 	if(!d)                 
                 	{                      
                 	  def dni = parsedEvent.mac
                      log.trace "Adding new device $dni Hub ID ${parsedEvent.hub}"
                 	  d = addChildDevice("kineticcare", "Home Health Station", dni, parsedEvent.hub, ["label":bridge.value.name])     
                      log.trace "Added new device"
                      
					  log.debug "created ${d.displayName} with id ${d.deviceNetworkId}"
                      def childDevice = getChildDevice(d.deviceNetworkId)
                      childDevice.sendEvent(name: "serialNumber", value: bridge.value.serialNumber)
 				      if (bridge.value.ip && bridge.value.port) {
                	  	if (bridge.value.ip.contains(".")) {
                        		log.trace "Path 1"
                    	  		childDevice.sendEvent(name: "networkAddress", value: bridge.value.ip + ":" +  bridge.value.port)
                          		childDevice.updateDataValue("networkAddress", bridge.value.ip + ":" +  bridge.value.port)
                       		} else {
                            	log.trace "Path 2"
                    			childDevice.sendEvent(name: "networkAddress", value: convertHexToIP(bridge.value.ip) + ":" +  convertHexToInt(bridge.value.port))	
                        		childDevice.updateDataValue("networkAddress", convertHexToIP(bridge.value.ip) + ":" +  convertHexToInt(bridge.value.port))
                       		}
                       }
					  else {
                        log.trace "Path 3"
					    childDevice.sendEvent(name: "networkAddress", value: convertHexToIP(bridge.value.networkAddress) + ":" +  convertHexToInt(bridge.value.deviceAddress))
                        childDevice.updateDataValue("networkAddress", convertHexToIP(bridge.value.networkAddress) + ":" +  convertHexToInt(bridge.value.deviceAddress))
                		}                          
                 	}
                 	else
                    	log.trace "Device ALready Exists"
                        
              		sendHubCommand(new physicalgraph.device.HubAction([
						method: "GET",
						path: "/stsubscribe/${hub.id}",
						headers: [
						HOST: bridge.value.networkAddress
							]], deviceNetworkId))   
				} else {
                	log.trace "ERROR ERROR"
					log.error "/description.xml returned a bridge that didn't exist"
				}
			}
		} else if(headerString?.contains("json")) {
            log.trace "description.xml response (application/json)"
			def body = new groovy.json.JsonSlurper().parseText(parsedEvent.body)
            if(body.subscribed != null){
            	log.trace "Subscription Status: ${body.subscribed}"
            }
			else if (body.success != null) {
				if (body.success[0] != null) {
					if (body.success[0].username)
						state.username = body.success[0].username
				}
			} else if (body.error != null) {
				//TODO: handle retries...
				log.error "ERROR: application/json ${body.error}"
			} else {
				//GET /api/${state.username}/lights response (application/json)
				if (!body?.state?.on) { //check if first time poll made it here by mistake
					def bulbs = getHueBulbs()
					log.debug "Adding bulbs to state!"
					body.each { k,v ->
						bulbs[k] = [id: k, name: v.name, type: v.type, hub:parsedEvent.hub]
					}
				}
			}
		}
	}
}*/

def doDeviceSync(){
	log.trace "Doing Hue Device Sync!"
	convertBulbListToMap()
	poll()
    try {
		subscribe(location, null, locationHandler, [filterEvents:false])
    } catch (all) {
    	log.trace "Subscription already exist"
 	}
	discoverBridges()
}

/////////////////////////////////////
//CHILD DEVICE METHODS
/////////////////////////////////////

def parse(description) {
	def parsedEvent = parseLanMessage(description) 
	def headerString = parsedEvent.headers.toString()
       def bodyString = parsedEvent.body.toString()
		log.trace "--------->>>>>> MESSAGE $headerString $bodyString"
}



def parse(childDevice, description) {	
	def parsedEvent = parseLanMessage(description) 
    
	if (parsedEvent.headers && parsedEvent.body) {
		def headerString = parsedEvent.headers.toString()
        def bodyString = parsedEvent.body.toString()
		log.trace "--------->>>>>> MESSAGE $headerString $bodyString"
    
    if (headerString?.contains("json")) { 
        	def body
        	try {
            	body = new groovy.json.JsonSlurper().parseText(bodyString)
            } catch (all) {
            	log.warn "Parsing Body failed - trying again..."
                poll()
            }
            if (body instanceof java.util.HashMap) { 
            	//poll response
                def bulbs = getChildDevices()
                for (bulb in body) {
                    def d = bulbs.find{it.deviceNetworkId == "${app.id}/${bulb.key}"}    
                    if (d) {
                        if (bulb.value.state?.reachable) {
                            sendEvent(d.deviceNetworkId, [name: "switch", value: bulb.value?.state?.on ? "on" : "off"])
                            sendEvent(d.deviceNetworkId, [name: "level", value: Math.round(bulb.value.state.bri * 100 / 255)])
                            if (bulb.value.state.sat) {
                                def hue = Math.min(Math.round(bulb.value.state.hue * 100 / 65535), 65535) as int
                                def sat = Math.round(bulb.value.state.sat * 100 / 255) as int
                                def hex = colorUtil.hslToHex(hue, sat)
                                sendEvent(d.deviceNetworkId, [name: "color", value: hex])
                                sendEvent(d.deviceNetworkId, [name: "hue", value: hue])
                                sendEvent(d.deviceNetworkId, [name: "saturation", value: sat])
                            }
                        } else {
                            sendEvent(d.deviceNetworkId, [name: "switch", value: "off"])
                            sendEvent(d.deviceNetworkId, [name: "level", value: 100])                     
                            if (bulb.value.state.sat) {
                                def hue = 23
                                def sat = 56
                                def hex = colorUtil.hslToHex(23, 56)
                                sendEvent(d.deviceNetworkId, [name: "color", value: hex])
                                sendEvent(d.deviceNetworkId, [name: "hue", value: hue])
                                sendEvent(d.deviceNetworkId, [name: "saturation", value: sat])                               
                            }    
                        }
                    }
                }     
            }
            else
            { //put response
                def hsl = [:]
                body.each { payload ->
                    log.debug $payload
                    if (payload?.success)
                    {
                        def childDeviceNetworkId = app.id + "/"
                        def eventType
                        body?.success[0].each { k,v ->
                            childDeviceNetworkId += k.split("/")[2]
                            if (!hsl[childDeviceNetworkId]) hsl[childDeviceNetworkId] = [:]
                            eventType = k.split("/")[4]
                            log.debug "eventType: $eventType"
                            switch(eventType) {
                                case "on":
                                    sendEvent(childDeviceNetworkId, [name: "switch", value: (v == true) ? "on" : "off"])
                                    break
                                case "bri":
                                    sendEvent(childDeviceNetworkId, [name: "level", value: Math.round(v * 100 / 255)])
                                    break
                                case "sat":
                                    hsl[childDeviceNetworkId].saturation = Math.round(v * 100 / 255) as int
                                    break
                                case "hue":
                                    hsl[childDeviceNetworkId].hue = Math.min(Math.round(v * 100 / 65535), 65535) as int
                                    break
                            }
                        }

                    }
                    else if (payload.error)
                    {
                        log.debug "JSON error - ${body?.error}"
                    }

                }

                hsl.each { childDeviceNetworkId, hueSat ->
                    if (hueSat.hue && hueSat.saturation) {
                        def hex = colorUtil.hslToHex(hueSat.hue, hueSat.saturation)
                        log.debug "sending ${hueSat} for ${childDeviceNetworkId} as ${hex}"
                        sendEvent(hsl.childDeviceNetworkId, [name: "color", value: hex])
                    }
                }

            }
    	}        
	} else {
		log.debug "parse - got something other than headers,body..."
		return []
	}
}

def on(childDevice) {

}

def off(childDevice) {

}


def nextLevel(childDevice) {
	def level = device.latestValue("level") as Integer ?: 0
	if (level < 100) {
		level = Math.min(25 * (Math.round(level / 25) + 1), 100) as Integer
	}
	else {
		level = 25
	}
	setLevel(childDevice,level)
}

private getId(childDevice) {
	if (childDevice.device?.deviceNetworkId?.startsWith("HUE")) {
		return childDevice.device?.deviceNetworkId[3..-1]
	}
	else {
		return childDevice.device?.deviceNetworkId.split("/")[-1]
	}
}

private poll() {
	def host = getBridgeIP()
	def uri = "/api/${state.username}/lights/"
    try {
		sendHubCommand(new physicalgraph.device.HubAction("""GET ${uri} HTTP/1.1
HOST: ${host}

""", physicalgraph.device.Protocol.LAN, selectedHue))
	} catch (all) {
        log.warn "Parsing Body failed - trying again..."
        doDeviceSync()
    }
}

private put(path, body) {
	def host = getBridgeIP()	
	def uri = "/api/${state.username}/$path"
	def bodyJSON = new groovy.json.JsonBuilder(body).toString()
	def length = bodyJSON.getBytes().size().toString()

	log.debug "PUT:  $host$uri"
	log.debug "BODY: ${bodyJSON}"

	sendHubCommand(new physicalgraph.device.HubAction("""PUT $uri HTTP/1.1
HOST: ${host}
Content-Length: ${length}

${bodyJSON}
""", physicalgraph.device.Protocol.LAN, "${selectedHue}"))

}

private getBridgeIP() {
	def host = null
	if (selectedHue) {
        def d = getChildDevice(selectedHue)
    	if (d) {
        	if (d.getDeviceDataByName("networkAddress"))
            	host =  d.getDeviceDataByName("networkAddress")
            else
        		host = d.latestState('networkAddress').stringValue
        }    
        if (host == null || host == "") {
            def serialNumber = selectedHue
            def bridge = getHueBridges().find { it?.value?.serialNumber?.equalsIgnoreCase(serialNumber) }?.value
            if (!bridge) { 
            	bridge = getHueBridges().find { it?.value?.mac?.equalsIgnoreCase(serialNumber) }?.value
            }
            if (bridge?.ip && bridge?.port) {
            	if (bridge?.ip.contains("."))
            		host = "${bridge?.ip}:${bridge?.port}"
                else
                	host = "${convertHexToIP(bridge?.ip)}:${convertHexToInt(bridge?.port)}"
            } else if (bridge?.networkAddress && bridge?.deviceAddress)
            	host = "${convertHexToIP(bridge?.networkAddress)}:${convertHexToInt(bridge?.deviceAddress)}"
        }    
        log.trace "Bridge: $selectedHue - Host: $host"
    }    
    return host
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}


private String convertHexToIP(hex) {
	if(hex)
		[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
    else
    	return "IP - ? ? ? ?"
}


private Boolean hasAllHubsOver(String desiredFirmware) {
	return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private playAudio(){
	sonos.playSoundAndTrack(state.sound.uri, state.sound.duration, state.selectedSong, volume)
}



private loadText() {
	switch ( actionType) {
		case "Bell 1":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/bell1.mp3", duration: "10"]
			break;
		case "Bell 2":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/bell2.mp3", duration: "10"]
			break;
		case "Dogs Barking":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/dogs.mp3", duration: "10"]
			break;
		case "Fire Alarm":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/alarm.mp3", duration: "17"]
			break;
		case "The mail has arrived":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/the+mail+has+arrived.mp3", duration: "1"]
			break;
		case "A door opened":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/a+door+opened.mp3", duration: "1"]
			break;
		case "There is motion":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/there+is+motion.mp3", duration: "1"]
			break;
		case "Smartthings detected a flood":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/smartthings+detected+a+flood.mp3", duration: "2"]
			break;
		case "Smartthings detected smoke":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/smartthings+detected+smoke.mp3", duration: "1"]
			break;
		case "Someone is arriving":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/someone+is+arriving.mp3", duration: "1"]
			break;
		case "Piano":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/piano2.mp3", duration: "10"]
			break;
		case "Lightsaber":
			state.sound = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/lightsaber.mp3", duration: "10"]
			break;
		default:
			if (message) {
				state.sound = textToSpeech(message instanceof List ? message[0] : message) // not sure why this is (sometimes) needed)
			}
			else {
				state.sound = textToSpeech("You selected the custom message option but did not enter a message in the $app.label Smart App")
			}
			break;
	}
}