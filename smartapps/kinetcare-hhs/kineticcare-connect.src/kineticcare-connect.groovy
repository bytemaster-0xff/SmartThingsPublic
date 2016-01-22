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
	name: "KineticCare (Connect)",
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
	page(name:"hhsAdded", title:"Added Home Health Station", content:"hhsAdded", refreshTimeout:5)
}

def mainPage() {
    def bridges = bridgesDiscovered()
    if (state.username && bridges) {
        return hhsAdded()
    } else {
        return hhsDiscovery()
    }
}

def hhsDiscovery(params=[:])
{
	def bridges = bridgesDiscovered()
	int bridgeRefreshCount = !state.bridgeRefreshCount ? 0 : state.bridgeRefreshCount as int
	state.bridgeRefreshCount = bridgeRefreshCount + 1
	def refreshInterval = 3

	def options = bridges ?: []
	def numFound = options.size() ?: 0

	if (numFound == 0 && state.bridgeRefreshCount > 25) {
    	log.trace "Cleaning old bridges memory"
    	state.bridges = [:]
        state.bridgeRefreshCount = 0
        app.updateSetting("selectedHue", "")
    }    

	subscribe(location, null, locationHandler, [filterEvents:false])

	//bridge discovery request every 15 //25 seconds
	if((bridgeRefreshCount % 5) == 0) {
		discoverBridges()
	}

	//setup.xml request every 3 seconds except on discoveries
	if(((bridgeRefreshCount % 1) == 0) && ((bridgeRefreshCount % 5) != 0)) {
		verifyHueBridges()
	}

	return dynamicPage(name:"hhsDiscovery", title:"Discovery Started!", nextPage:"hhsAdded", refreshInterval:refreshInterval, uninstall: true) {
		section("Please wait while we discover your Home Health Station. Discovery can take five minutes or more! Select your device below once discovered.") {
			input "selectedHue", "enum", required:false, title:"Select Hue Bridge (${numFound} found)", multiple:false, options:options
		}
	}
}

def hhsAdded(params=[:]){
	return dynamicPage(name:"hhsAdded", title:"Home Health Station Added", nextPage:"", refreshInterval:refreshInterval, install:true, uninstall: true) {
		section("Congratulations, your Home Health Station has been Added.") 
	}
}


private discoverBridges() {
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:basic:1", physicalgraph.device.Protocol.LAN))
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
		sendHubCommand(new physicalgraph.device.HubAction([
		method: "GET",
		path: "/xml/props.xml",
		headers: [
			HOST: host
		]], deviceNetworkId))
}

private verifyHueBridges() {
	def devices = getHueBridges().findAll { it?.value?.verified != true }
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

def locationHandler(evt) {
	def description = evt.description
    log.trace "Location: $description"

	def hub = evt?.hubId
 	def parsedEvent = parseLanMessage(description)
	parsedEvent << ["hub":hub]

	if (parsedEvent?.ssdpTerm?.contains("urn:schemas-upnp-org:device:Basic:1")) {
    	//SSDP DISCOVERY EVENTS
		log.trace "SSDP DISCOVERY EVENTS"
		def bridges = getHueBridges()
		log.trace bridges.toString()
		if (!(bridges."${parsedEvent.ssdpUSN.toString()}")) {
        	//bridge does not exist 
			log.trace ">>>>>>>Adding bridge ${parsedEvent.ssdpUSN}"
			bridges << ["${parsedEvent.ssdpUSN.toString()}":parsedEvent]
		} else {
			// update the values
            def ip = convertHexToIP(parsedEvent.networkAddress)
            def port = convertHexToInt(parsedEvent.deviceAddress)
            def host = ip + ":" + port
			log.debug "Device ($parsedEvent.mac) was already found in state with ip = $host."
            
                             
            sendHubCommand(new physicalgraph.device.HubAction([
                method: "GET",
                path: "/stsubscribe",
                headers: [
                    HOST: host
                ]], deviceNetworkId))   
            
            def dstate = bridges."${parsedEvent.ssdpUSN.toString()}"
			def dni = "${parsedEvent.mac}"
            def d = getChildDevice(dni)
            def networkAddress = null
            if (!d) {
            	log.debug "Device was null"
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
                log.trace "Host: $host - $networkAddress"
                
                if(host != networkAddress) {
                    log.debug "Device's port or ip changed for device $d..."
                    dstate.ip = ip
                    dstate.port = port
                    dstate.name = "Philips hue ($ip)"
                    d.sendEvent(name:"networkAddress", value: host)
                    d.updateDataValue("networkAddress", host)
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
						path: "/stsubscribe",
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
			if (body.success != null) {
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
	} else {
		log.trace "NON-HUE EVENT $evt.description"
	}
}

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

def parse(childDevice, description) {
	def parsedEvent = parseLanMessage(description) 
	if (parsedEvent.headers && parsedEvent.body) {
		def headerString = parsedEvent.headers.toString()
        def bodyString = parsedEvent.body.toString()
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
	log.debug "Executing 'on'"
	put("lights/${getId(childDevice)}/state", [on: true])
    return "Bulb is On"
}

def off(childDevice) {
	log.debug "Executing 'off'"
	put("lights/${getId(childDevice)}/state", [on: false])
    return "Bulb is Off"
}

def setLevel(childDevice, percent) {
	log.debug "Executing 'setLevel'"
    def level 
    if (percent == 1) level = 1 else level = Math.min(Math.round(percent * 255 / 100), 255)
	put("lights/${getId(childDevice)}/state", [bri: level, on: percent > 0])
}

def setSaturation(childDevice, percent) {
	log.debug "Executing 'setSaturation($percent)'"
	def level = Math.min(Math.round(percent * 255 / 100), 255)
	put("lights/${getId(childDevice)}/state", [sat: level])
}

def setHue(childDevice, percent) {
	log.debug "Executing 'setHue($percent)'"
	def level =	Math.min(Math.round(percent * 65535 / 100), 65535)
	put("lights/${getId(childDevice)}/state", [hue: level])
}

def setColor(childDevice, huesettings) {
	log.debug "Executing 'setColor($huesettings)'"
	def hue = Math.min(Math.round(huesettings.hue * 65535 / 100), 65535)
	def sat = Math.min(Math.round(huesettings.saturation * 255 / 100), 255)
    def alert = huesettings.alert ? huesettings.alert : "none"
    def transition = huesettings.transition ? huesettings.transition : 4

	def value = [sat: sat, hue: hue, alert: alert, transitiontime: transition]
	if (huesettings.level != null) {
        if (huesettings.level == 1) value.bri = 1 else value.bri = Math.min(Math.round(huesettings.level * 255 / 100), 255)
		value.on = value.bri > 0
	}

	if (huesettings.switch) {
		value.on = huesettings.switch == "on"
	}

	log.debug "sending command $value"
	put("lights/${getId(childDevice)}/state", value)
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

def convertBulbListToMap() {
	try {
		if (state.bulbs instanceof java.util.List) {
			def map = [:]
			state.bulbs.unique {it.id}.each { bulb ->
				map << ["${bulb.id}":["id":bulb.id, "name":bulb.name, "hub":bulb.hub]]
			}
			state.bulbs = map
		}
	}
	catch(Exception e) {
		log.error "Caught error attempting to convert bulb list to map: $e"
	}
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private Boolean canInstallLabs() {
	return hasAllHubsOver("000.011.00603")
}

private Boolean hasAllHubsOver(String desiredFirmware) {
	return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions() {
	return location.hubs*.firmwareVersionString.findAll { it }
}